"""Fuzz color frames against a Govee strip directly from PC.

User watches the strip and reports which step number turned it red.

Usage:
    python fuzz_govee.py --mac D9:31:30:30:35:3B            # run all candidates
    python fuzz_govee.py --mac D9:31:30:30:35:3B --step 4   # repeat single step
"""
import argparse
import asyncio
import sys

from bleak import BleakClient

WRITE = "00010203-0405-0607-0809-0a0b0c0d2b11"
KEEP_ALIVE = bytes.fromhex("aa010000000000000000000000000000000000ab")


def frame(payload: bytes) -> bytes:
    body = payload.ljust(19, b"\x00")[:19]
    c = 0
    for b in body:
        c ^= b
    return body + bytes([c])


RED = (0xFF, 0x00, 0x00)
GREEN = (0x00, 0xFF, 0x00)

CANDIDATES = [
    ("A H6125 33 05 15 01 + mask@8", bytes([0x33, 0x05, 0x15, 0x01, *RED, 0x00, 0x00, 0xFF, 0x7F])),
    ("B legacy 33 05 02", bytes([0x33, 0x05, 0x02, *RED])),
    ("C 33 05 0B + mask@8", bytes([0x33, 0x05, 0x0B, *RED, 0x00, 0x00, 0xFF, 0x7F])),
    ("D H6054-layout mask@12", bytes([0x33, 0x05, 0x15, 0x01, *RED, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0x7F])),
    ("E gradient OFF", bytes([0x33, 0xA3, 0x00])),
    ("F left-half only", bytes([0x33, 0x05, 0x0B, *RED, 0x00, 0x00, 0xFF, 0x00])),
    ("G right-half only", bytes([0x33, 0x05, 0x0B, *RED, 0x00, 0x00, 0x00, 0x7F])),
    ("H no mask at all", bytes([0x33, 0x05, 0x15, 0x01, *RED])),
    ("I scene Sunrise then color", None),  # special: two packets
    ("J music-mode exit + color", None),  # special: gradient on, off, color
]


async def send(c: BleakClient, payload: bytes, label: str):
    pkt = frame(payload)
    await c.write_gatt_char(WRITE, pkt, response=False)
    print(f"  -> {label}: {pkt.hex()}", flush=True)


async def main(mac: str, step: int, rgb=None, gap: float = 3.0):
    ink = tuple(rgb) if rgb else RED
    cands = []
    for label, payload in CANDIDATES:
        if payload is None:
            cands.append((label, None))
            continue
        p = bytearray(payload)
        # replace the first RR GG BB occurrence after byte 3 with chosen ink
        if len(p) >= 7 and p[0] == 0x33 and p[1] == 0x05:
            p[4], p[5], p[6] = ink
        cands.append((label, bytes(p)))
    async with BleakClient(mac, timeout=20) as c:
        await asyncio.sleep(0.3)
        print("connected, turning ON + brightness 100", flush=True)
        await send(c, bytes([0x33, 0x01, 0x01]), "power ON")
        await asyncio.sleep(0.5)
        await send(c, bytes([0x33, 0x04, 100]), "brightness 100")
        await asyncio.sleep(1.0)

        steps = [step] if step else range(1, len(CANDIDATES) + 1)
        for n in steps:
            label, payload = cands[n - 1]
            print(f"STEP {n}: {label} — watch the strip!", flush=True)
            if n == 9:  # I
                await send(c, bytes([0x33, 0x05, 0x04, 0x00]), "scene Sunrise")
                await asyncio.sleep(2.0)
                await send(c, cands[0][1], "color A after scene")
            elif n == 10:  # J
                await send(c, bytes([0x33, 0xA3, 0x01]), "gradient ON")
                await asyncio.sleep(1.0)
                await send(c, bytes([0x33, 0xA3, 0x00]), "gradient OFF")
                await asyncio.sleep(1.0)
                await send(c, cands[0][1], "color A after gradient")
            else:
                await send(c, payload, label)
            await c.write_gatt_char(WRITE, KEEP_ALIVE, response=False)
            await asyncio.sleep(gap)
        print("done — report which STEP turned the strip red (if any)", flush=True)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--mac", required=True)
    ap.add_argument("--step", type=int, default=0)
    ap.add_argument("--rgb", default="", help="RRGGBB ink, e.g. 00FF00")
    ap.add_argument("--gap", type=float, default=3.0)
    ap.add_argument("--steps", default="", help="comma list e.g. 5,1,2,3")
    a = ap.parse_args()
    rgb = bytes.fromhex(a.rgb) if a.rgb else None
    if a.steps:
        import asyncio as _a
        async def _run():
            async with BleakClient(a.mac, timeout=20) as c:
                await asyncio.sleep(0.3)
                print("connected", flush=True)
                # rebuild cands identically
                from fuzz_govee import CANDIDATES as _C, frame as _f, WRITE as _W, KEEP_ALIVE as _K
                cands = []
                for label, payload in _C:
                    if payload is None:
                        cands.append((label, None)); continue
                    p = bytearray(payload)
                    if len(p) >= 7 and p[0] == 0x33 and p[1] == 0x05:
                        p[4], p[5], p[6] = tuple(rgb) if rgb else (p[4], p[5], p[6])
                    cands.append((label, bytes(p)))
                for i, n in enumerate([int(x) for x in a.steps.split(',')], 1):
                    label, payload = cands[n - 1]
                    print(f"FLASH {i}: (step {n}) {label} — watch!", flush=True)
                    body = payload.ljust(19, b'\x00')[:19]
                    ch = 0
                    for b in body: ch ^= b
                    await c.write_gatt_char(_W, body + bytes([ch]), response=False)
                    await c.write_gatt_char(_W, _K, response=False)
                    await _a.sleep(a.gap)
        _a.run(_run())
    else:
        asyncio.run(main(a.mac, a.step, rgb, a.gap))
