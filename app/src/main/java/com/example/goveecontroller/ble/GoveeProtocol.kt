package com.example.goveecontroller.ble

import java.util.UUID

/**
 * Reverse-engineered Govee BLE protocol.
 *
 * Sources: coding.kiwi H6054 writeup, Obi2000 H6199 repo,
 * egold555 H6113 repo, allepet H6125 gist, chvolkmann govee_btled.
 *
 * Your strip H6125 (H61253D2DE, RGBIC 5m, BT-only):
 *  - Service ......... 00010203-0405-0607-0809-0a0b0c0d1910
 *  - Write (no resp).. 00010203-0405-0607-0809-0a0b0c0d2b11
 *  - Notify/read ..... 00010203-0405-0607-0809-0a0b0c0d2b10
 *  - All commands 20 bytes, last byte = XOR of first 19.
 *  - Write with WRITE_TYPE_NO_RESPONSE (ATT opcode 0x52).
 *  - App sends keep-alive AA 01 00…00 AB every ~2s or the strip drops you (~10s idle).
 */
object GoveeProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d1910")
    val WRITE_UUID: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b11")
    val NOTIFY_UUID: UUID = UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b10")

    /** Keep-alive, fixed. Must be sent every ~2s while connected. */
    val KEEP_ALIVE: ByteArray = hex("aa010000000000000000000000000000000000ab")

    fun checksum19(body19: ByteArray): Byte {
        require(body19.size == 19) { "body must be 19 bytes" }
        var c = 0
        for (b in body19) c = c xor (b.toInt() and 0xFF)
        return c.toByte()
    }

    /** Pad 19-byte body + XOR checksum → 20-byte packet. */
    private fun frame(payload: ByteArray): ByteArray {
        val body = ByteArray(19)
        payload.copyInto(body, 0, 0, minOf(payload.size, 19))
        return body + checksum19(body)
    }

    fun power(on: Boolean): ByteArray =
        frame(byteArrayOf(0x33, 0x01, if (on) 0x01 else 0x00))

    /** brightness 0..100 */
    fun brightness(percent: Int): ByteArray {
        val p = percent.coerceIn(0, 100)
        return frame(byteArrayOf(0x33, 0x04, p.toByte()))
    }

    /**
     * Solid color for H6125 RGBIC — VERIFIED on H6125_5242 via camera loop.
     * Format: 33 05 15 01 RR GG BB 00 00 00 00 00 FF 7F …
     * bytes 12-13 (FF 7F) = segment mask "all 15 segments".
     * (Published docs put the mask at bytes 8-9 — this firmware wants 12-13.)
     */
    fun color(r: Int, g: Int, b: Int): ByteArray = frame(
        byteArrayOf(
            0x33, 0x05, 0x15, 0x01,
            r.toByte(), g.toByte(), b.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00,
            0xFF.toByte(), 0x7F,
        )
    )

    /**
     * Per-segment color (same mask@12 layout as the verified base frame).
     * leftMask bits 0-7 → segments 1-8, rightMask bits 0-6 → segments 9-15.
     */
    fun segmentColor(r: Int, g: Int, b: Int, leftMask: Int, rightMask: Int): ByteArray =
        frame(
            byteArrayOf(
                0x33, 0x05, 0x15, 0x01,
                r.toByte(), g.toByte(), b.toByte(),
                0x00, 0x00, 0x00, 0x00, 0x00,
                leftMask.toByte(), rightMask.toByte(),
            )
        )

    /** Legacy non-RGBIC solid color (H61xx basic strips): 33 05 02 RR GG BB … */
    fun colorLegacy(r: Int, g: Int, b: Int): ByteArray = frame(
        byteArrayOf(0x33, 0x05, 0x02, r.toByte(), g.toByte(), b.toByte())
    )

    /** Gradient on/off (H6199/H6125 family): 33 A3 01 / 33 A3 00 */
    fun gradient(on: Boolean): ByteArray =
        frame(byteArrayOf(0x33, 0xA3.toByte(), if (on) 0x01 else 0x00))

    /** Scene IDs validated on H6199; H6125 shares most of them — verify in Lab tab. */
    fun scene(id: Int): ByteArray =
        frame(byteArrayOf(0x33, 0x05, 0x04, id.toByte()))

    /**
     * Music activation, VERIFIED on H6125_5242 (flicker 51 vs 0.17 baseline).
     * Sequence: brightness → palette frags (x2, Rule 5) → activate.
     */
    fun musicActivate(profile: Int, sensitivity: Int): ByteArray = frame(
        byteArrayOf(0x33, 0x05, 0x13, profile.toByte(), sensitivity.toByte())
    )

    /**
     * Palette upload fragments for music mode (max 4 colors).
     * frag1: a3 00 01 02 41 <profile> <count> <RGB…≤12B>, frag2: a3 ff <rest>.
     */
    fun musicPalette(profile: Int, colors: List<Triple<Int, Int, Int>>): List<ByteArray> {
        require(colors.size in 1..4) { "1..4 palette colors" }
        val flat = colors.flatMap { listOf(it.first, it.second, it.third) }
            .map { it.coerceIn(0, 255).toByte() }.toByteArray()
        val first12 = flat.copyOfRange(0, minOf(12, flat.size))
        val rest = if (flat.size > 12) flat.copyOfRange(12, flat.size) else byteArrayOf()
        val frag1 = byteArrayOf(
            0xA3.toByte(), 0x00, 0x01, 0x02, 0x41,
            profile.toByte(), colors.size.toByte(),
        ) + first12
        val frag2 = byteArrayOf(0xA3.toByte(), 0xFF.toByte()) +
            rest + if (rest.isNotEmpty()) byteArrayOf(0x01) else byteArrayOf()
        return listOf(frame(frag1), frame(frag2))
    }

    /** Solid color also EXITS music mode (verified: flicker drops to ~0.1). */
    fun musicStop(r: Int, g: Int, b: Int): ByteArray = color(r, g, b)

    fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun hex(s: String): ByteArray {
        val clean = s.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        require(clean.length % 2 == 0 && clean.length <= 40) { "need 1..20 bytes hex" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /** Returns null if packet fails XOR check. */
    fun validate(packet: ByteArray): Boolean {
        if (packet.size != 20) return false
        var c = 0
        for (i in 0 until 19) c = c xor (packet[i].toInt() and 0xFF)
        return (c and 0xFF) == (packet[19].toInt() and 0xFF)
    }
}

/** Music styles measured live on H6125_5242 (flicker vs 0.17 solid baseline). */
enum class MusicProfile(val id: Int, val label: String, val note: String) {
    RHYTHM(0x03, "Rhythm", "strong · verified"),
    PULSE(0x05, "Pulse", "medium · verified"),
    FLOW(0x33, "Flow", "gentle · verified"),
}

/** Scene catalog (H6199-verified, mostly shared with H6125). Star your working ones in-app. */
enum class GoveeScene(val id: Int, val label: String) {
    SUNRISE(0x00, "Sunrise"),
    SUNSET(0x01, "Sunset"),
    MOVIE(0x04, "Movie"),
    DATING(0x05, "Dating"),
    ROMANTIC(0x07, "Romantic"),
    TWINKLE(0x08, "Twinkle"),
    CANDLELIGHT(0x09, "Candlelight"),
    BREATHE(0x0A, "Breathe"),
    SNOWFLAKE(0x0F, "Snowflake"),
    ENERGETIC(0x10, "Energetic"),
    CROSSING(0x14, "Crossing"),
    RAINBOW(0x15, "Rainbow"),
}
