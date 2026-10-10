<div align="center">

# ✨ Govee Glow

**A Material 3 Expressive controller for Govee Bluetooth LED strips — no cloud, no account, no Govee app.**

![Platform](https://img.shields.io/badge/platform-Android%2026%2B-3DDC84?style=flat-square)
![UI](https://img.shields.io/badge/UI-Material%203%20Expressive-6750A4?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)
![Verified](https://img.shields.io/badge/packets-camera--verified-brightgreen?style=flat-square)

Turn your Bluetooth-only Govee strip into a first-class Android citizen:
wallpaper-matched theme, home-screen widgets, Quick Settings tile, Devices-panel
controls, music sync that actually syncs, smooth gradients, and sunrise/sunset automations.

</div>

| App | Proof: single-packet color | Proof: 15-zone gradient |
|---|---|---|
| ![App UI](assets/app-control.png) | ![Green, first try that worked](assets/verified-color.jpg) | ![Rainbow across 15 segments](assets/verified-gradient.jpg) |

> The photos on the right aren't renders — they're the author's actual H6125,
> photographed by a webcam while a fuzzer spoke to it over BLE. Every frame in
> this app earned its place the same way.

---

## Features

- 🎨 **Real color picker** — HSV square + hue ribbon + presets, animated glow preview
- 🌈 **Gradients** — 6 presets + custom 2-stop blends, painted as 15 per-segment writes
- 🎯 **15-zone RGBIC control** — paint any combination of segments
- 🎵 **Music sync that works** — palette upload + measured profile bytes (see below), mic is on the strip
- 🔗 **Auto-link** — remembers your strip, connects on open, re-links on drop
- 🏠 **Widgets** — power toggle + brightness stepper, wallpaper-tinted (Monet)
- ⚡ **Quick Settings tile** + **Devices-panel** power/brightness controls
- ⏰ **Automations** — schedules (time + days + on/off/dim/color) and **sunset→ON / sunrise→OFF** with offsets, computed on-device, survives reboot
- 🧪 **Lab** — BLE scanner, raw 20-byte packet sender (auto-XOR), live packet log

## The protocol (H6125, camera-verified)

All commands: 20 bytes over `00010203-0405-0607-0809-0a0b0c0d2b11`
(WRITE_NO_RESPONSE), last byte = XOR of the first 19. Keep-alive
`aa0100…ab` every ~2 s or the strip drops you.

| Action | Frame |
|---|---|
| ON / OFF | `33 01 01` / `33 01 00` |
| Brightness 0–100 | `33 04 <pct>` |
| Solid color | `33 05 15 01 RR GG BB 00 00 00 00 00 FF 7F` |
| Per-segment | `33 05 15 01 RR GG BB 00 00 00 00 00 LEFT RIGHT` |
| Music palette | `a3 00 01 02 41 <profile> <n> <RGB…>` → `a3 ff …` (send twice) |
| Music start | `33 05 13 <profile> <sensitivity 0–99>` |
| Music stop | any solid-color frame |
| Gradient | 15× segment writes, interpolated |

Two things the public writeups get wrong for this firmware:

1. **The segment mask sits at bytes 12–13, not 8–9.** Every doc says
   `…RR GG BB 00 00 FF 7F`; the H6125 wants five zero bytes first. Found by
   fuzzing 10 candidates while a webcam watched the strip.
2. **Music is `33 05 13`, not `33 05 0C`.** Measured profiles on H6125:
   `0x03` Rhythm (strong), `0x05` Pulse (medium), `0x33` Flow (gentle).
   Profile IDs are per-device — don't copy them to other models.

Pure-Kotlin, dependency-free implementation lives in
`app/src/main/java/com/example/goveecontroller/ble/GoveeProtocol.kt`.
A matching Python harness (`tools/`: scanner, fuzzer, camera-in-the-loop
verifier, music profiler, gradient painter) is included so you can re-derive
any of this on your own strip.

## Get it

**Option A — APK:** grab `app-debug.apk` from
[Releases](../../releases) and sideload it (Android 8+).

**Option B — build it:** open this folder in Android Studio (Ladybug+),
run on a physical phone (emulators can't do BLE).

```bash
pip install bleak opencv-python   # only for tools/
python tools/test_govee.py --scan # sanity-check your strip first
```

> Force-close the official Govee app first — BT-only strips hold one
> connection at a time.

## How it was built

Reverse-engineered from public research (coding.kiwi H6054, Obi2000 H6199,
egold555 H6113, allepet H6125, chvolkmann `govee_btled`, paulo's music-mode
writeup), then every unknown byte settled empirically: a PC spoke BLE to the
strip while a webcam watched it — red→green flips, flicker-vs-beeps profiling,
15-zone rainbow photos. The `tools/` folder is the lab notebook.

## Disclaimer

Clean-room interoperability work: only short command frames are reproduced,
no Govee app code, assets, or keys. Made with affection for a good product —
if you work at Govee and want anything changed, open an issue.

## Credits

The researchers above for publishing their findings, and one very patient
LED strip (H6125_5242) that blinked through ~200 test packets without complaint.
