"""Verify single-segment masks + smooth gradient on H6125 via webcam.

1. Paint segment 1 red, segment 15 blue (single-bit masks @12).
2. Rainbow gradient across all 15 segments, photo.
Usage:
    python verify_gradient.py --mac D8:BF:C4:06:52:42 --cam 0
"""
import argparse
import asyncio
import colorsys

import cv2
from bleak import BleakClient

WRITE = "00010203-0405-0607-0809-0a0b0c0d2b11"
KEEP_ALIVE = bytes.fromhex("aa010000000000000000000000000000000000ab")


def frame(payload: bytes) -> bytes:
    body = payload.ljust(19, b"\x00")[:19]
    c = 0
    for b in body:
        c ^= b
    return body + bytes([c])


def seg_mask(s: int):
    if s <= 8:
        return (1 << (s - 1)), 0
    return 0, (1 << (s - 9))


def seg_color(r, g, b, left, right):
    return frame(bytes([0x33, 0x05, 0x15, 0x01, r, g, b, 0, 0, 0, 0, 0, left, right]))


def snap(cap):
    for _ in range(3):
        cap.read()
    ok, f = cap.read()
    return f if ok else None


async def main(mac: str, cam: int):
    async with BleakClient(mac, timeout=20) as c:
        await asyncio.sleep(0.3)

        async def send(pkt: bytes):
            await c.write_gatt_char(WRITE, pkt, response=False)

        await send(frame(bytes([0x33, 0x01, 0x01])))
        await asyncio.sleep(0.4)
        await send(frame(bytes([0x33, 0x04, 100])))
        await asyncio.sleep(0.5)

        cap = cv2.VideoCapture(cam, cv2.CAP_DSHOW)
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
        for _ in range(8):
            cap.read()

        # test 1: seg1 red, seg15 blue
        l, r = seg_mask(1)
        await send(seg_color(255, 0, 0, l, r))
        await asyncio.sleep(0.4)
        l, r = seg_mask(15)
        await send(seg_color(0, 0, 255, l, r))
        await asyncio.sleep(2.0)
        img = snap(cap)
        cv2.imwrite("grad_segtest.jpg", img)
        print("seg1=red seg15=blue sent, photo saved", flush=True)

        # test 2: full rainbow gradient
        for s in range(1, 16):
            hue = (s - 1) / 15.0
            fr, fg, fb = colorsys.hsv_to_rgb(hue, 1.0, 1.0)
            l, r = seg_mask(s)
            await send(seg_color(int(fr * 255), int(fg * 255), int(fb * 255), l, r))
            await asyncio.sleep(0.15)
        await c.write_gatt_char(WRITE, KEEP_ALIVE, response=False)
        await asyncio.sleep(2.0)
        img = snap(cap)
        cv2.imwrite("grad_rainbow.jpg", img)
        print("rainbow sent, photo saved", flush=True)
        cap.release()


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--mac", required=True)
    ap.add_argument("--cam", type=int, default=0)
    a = ap.parse_args()
    asyncio.run(main(a.mac, a.cam))
