"""Quick protocol validation for Govee H6125 before building the APK.

Usage:
    pip install bleak
    python test_govee.py --scan                 # find your Govee_H6125_xxxx MAC
    python test_govee.py --mac AA:BB:CC:DD:EE:FF --color FF5500 --brightness 80
    python test_govee.py --mac AA:BB:CC:DD:EE:FF --on
    python test_govee.py --mac AA:BB:CC:DD:EE:FF --off
"""
import argparse
import asyncio
import sys

from bleak import BleakClient, BleakScanner

SERVICE = "00010203-0405-0607-0809-0a0b0c0d1910"
WRITE = "00010203-0405-0607-0809-0a0b0c0d2b11"

KEEP_ALIVE = bytes.fromhex("aa010000000000000000000000000000000000ab")


def frame(payload: bytes) -> bytes:
    body = payload.ljust(19, b"\x00")[:19]
    c = 0
    for b in body:
        c ^= b
    return body + bytes([c])


def pkt_power(on: bool) -> bytes:
    return frame(bytes([0x33, 0x01, 0x01 if on else 0x00]))


def pkt_brightness(p: int) -> bytes:
    return frame(bytes([0x33, 0x04, max(0, min(100, p))]))


def pkt_color(r: int, g: int, b: int) -> bytes:
    # H6125 RGBIC solid: 33 05 15 01 RR GG BB 00 00 FF 7F …
    return frame(bytes([0x33, 0x05, 0x15, 0x01, r, g, b, 0x00, 0x00, 0xFF, 0x7F]))


def pkt_color_legacy(r: int, g: int, b: int) -> bytes:
    return frame(bytes([0x33, 0x05, 0x02, r, g, b]))


async def scan():
    print("Scanning 10s for Govee devices…")
    devs = await BleakScanner.discover(timeout=10)
    for d in devs:
        flag = "  <-- GOVEE" if (d.name or "") and "govee" in d.name.lower() else ""
        print(f"{d.address}  {d.name}  rssi={d.rssi}{flag}")
    print("\nCopy the address starting with Govee_H6125.")


async def run(mac: str, on=None, off=False, color=None, brightness=None, legacy=False):
    async with BleakClient(mac, timeout=20) as c:
        await asyncio.sleep(0.3)  # Govee needs a beat before service discovery is stable
        svcs = c.services
        names = [s.uuid for s in svcs]
        if WRITE not in names and WRITE.lower() not in names:
            print("WARNING: Govee write char not found. Services:", names)
            print("Wrong device or model variant — aborting.")
            return
        await c.write_gatt_char(WRITE, KEEP_ALIVE, response=False)
        if on:
            await c.write_gatt_char(WRITE, pkt_power(True), response=False)
            print("sent ON:", pkt_power(True).hex())
        if off:
            await c.write_gatt_char(WRITE, pkt_power(False), response=False)
            print("sent OFF:", pkt_power(False).hex())
        if brightness is not None:
            await c.write_gatt_char(WRITE, pkt_brightness(brightness), response=False)
            print("sent brightness:", pkt_brightness(brightness).hex())
        if color:
            r, g, b = bytes.fromhex(color)
            pkt = pkt_color_legacy(r, g, b) if legacy else pkt_color(r, g, b)
            await c.write_gatt_char(WRITE, pkt, response=False)
            print("sent color:", pkt.hex())
        # keep alive a moment so the strip doesn't drop us mid-test
        await c.write_gatt_char(WRITE, KEEP_ALIVE, response=False)
        print("done ✓ — if nothing happened, try --legacy or capture HCI snoop (see README).")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--scan", action="store_true")
    ap.add_argument("--mac", default="")
    ap.add_argument("--on", action="store_true")
    ap.add_argument("--off", action="store_true")
    ap.add_argument("--color", default="", help="RRGGBB hex, e.g. FF5500")
    ap.add_argument("--brightness", type=int, default=None, help="0-100")
    ap.add_argument("--legacy", action="store_true", help="use 33 05 02 color variant")
    a = ap.parse_args()
    if a.scan:
        asyncio.run(scan())
    elif not a.mac:
        print("need --mac or --scan")
        sys.exit(2)
    else:
        asyncio.run(run(a.mac, a.on, a.off, a.color, a.brightness, a.legacy))
