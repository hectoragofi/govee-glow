"""Find the H6125 music profile byte, v2:
- rapid frame sampling (frame-to-frame diff std defeats auto-exposure drift)
- beeps on a background thread while sampling
- keep-alive between every phase (strip drops idle links after ~10s)

Usage:
    python verify_music.py --mac D8:BF:C4:06:52:42 --cam 0
"""
import argparse
import asyncio
import threading
import time
import winsound

import cv2
import numpy as np
from bleak import BleakClient

WRITE = "00010203-0405-0607-0809-0a0b0c0d2b11"
KEEP_ALIVE = bytes.fromhex("aa010000000000000000000000000000000000ab")


def frame(payload: bytes) -> bytes:
    body = payload.ljust(19, b"\x00")[:19]
    c = 0
    for b in body:
        c ^= b
    return body + bytes([c])


def palette_packets(profile: int, colors, brightness=80, sensitivity=70):
    flat = b"".join(bytes(c) for c in colors)
    rest = flat[12:]
    return [
        frame(bytes([0x33, 0x04, brightness])),
        frame(b"\xa3\x00\x01\x02\x41" + bytes([profile, len(colors)]) + flat[:12]),
        frame(b"\xa3\xff" + rest + (b"\x01" if rest else b"")),
        frame(bytes([0x33, 0x05, 0x13, profile, sensitivity])),
    ]


def snap(cap):
    for _ in range(2):
        cap.read()
    ok, f = cap.read()
    return f if ok else None


def brightness_of(img) -> float:
    h = img.shape[0]
    roi = img[: int(h * 0.45)].astype(float)
    return float(np.mean(np.maximum(np.maximum(roi[:, :, 0], roi[:, :, 1]), roi[:, :, 2])))


class Beeper(threading.Thread):
    def __init__(self):
        super().__init__(daemon=True)
        self.run_flag = True

    def run(self):
        while self.run_flag:
            winsound.Beep(988, 150)
            time.sleep(0.12)
            if not self.run_flag:
                break
            winsound.Beep(659, 150)
            time.sleep(0.12)

    def stop(self):
        self.run_flag = False


def flicker(cap, seconds: float, beep: bool) -> float:
    b = Beeper() if beep else None
    if b:
        b.start()
    vals = []
    t0 = time.time()
    while time.time() - t0 < seconds:
        img = snap(cap)
        if img is not None:
            vals.append(brightness_of(img))
        time.sleep(0.05)
    if b:
        b.stop()
        b.join()
    if len(vals) < 4:
        return 0.0
    diffs = np.diff(np.array(vals))
    return float(np.std(diffs))


async def main(mac: str, cam: int):
    async with BleakClient(mac, timeout=20) as c:
        await asyncio.sleep(0.3)

        async def send(payload: bytes):
            await c.write_gatt_char(WRITE, frame(payload), response=False)

        async def alive():
            await c.write_gatt_char(WRITE, KEEP_ALIVE, response=False)

        SOLID = bytes([0x33, 0x05, 0x15, 0x01, 0, 0, 255, 0, 0, 0, 0, 0, 0xFF, 0x7F])
        await send(bytes([0x33, 0x01, 0x01]))
        await asyncio.sleep(0.4)

        cap = cv2.VideoCapture(cam, cv2.CAP_DSHOW)
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
        for _ in range(8):
            cap.read()

        await send(SOLID)
        await asyncio.sleep(1.5)
        await alive()
        f_base = flicker(cap, 3.0, beep=True)
        print(f"BASELINE solid vs beeps: flicker={f_base:.2f}", flush=True)

        profiles = [0x02, 0x03, 0x05, 0x33, 0x38, 0x4B, 0x72, 0x83, 0x00, 0x01]
        colors = [(255, 0, 128), (0, 255, 255)]
        for prof in profiles:
            pkts = palette_packets(prof, colors)
            for pkt in pkts + pkts:  # Rule 5: send twice
                await c.write_gatt_char(WRITE, pkt, response=False)
                await asyncio.sleep(0.12)
            await alive()
            await asyncio.sleep(1.5)
            f_music = flicker(cap, 3.5, beep=True)
            await alive()
            print(f"profile {prof:#04x}: flicker={f_music:.2f}", flush=True)
            img = snap(cap)
            if img is not None:
                cv2.imwrite(f"music_{prof:02x}.jpg", img)
            await send(SOLID)
            await asyncio.sleep(1.0)
            await alive()
        cap.release()
        print("done", flush=True)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--mac", required=True)
    ap.add_argument("--cam", type=int, default=0)
    a = ap.parse_args()
    asyncio.run(main(a.mac, a.cam))
