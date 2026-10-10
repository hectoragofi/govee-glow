"""Closed-loop color fuzz: send candidate frames, watch strip via webcam, detect change.

Usage:
    python auto_fuzz.py --mac D8:BF:C4:06:52:42 --cam 0
"""
import argparse
import asyncio
import sys

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


GREEN = bytes([0x00, 0xFF, 0x00])


def color_variants():
    """(label, payload-bytes-with-RGB-placeholder) — RGB filled in by caller."""
    P = [
        ("A H6125 15 01 mask@8", bytes([0x33, 0x05, 0x15, 0x01, 0, 0, 0, 0x00, 0x00, 0xFF, 0x7F])),
        ("B legacy 05 02", bytes([0x33, 0x05, 0x02, 0, 0, 0])),
        ("C 05 0B mask@8", bytes([0x33, 0x05, 0x0B, 0, 0, 0, 0x00, 0x00, 0xFF, 0x7F])),
        ("D H6054 mask@12", bytes([0x33, 0x05, 0x15, 0x01, 0, 0, 0, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0x7F])),
        ("F left-half", bytes([0x33, 0x05, 0x0B, 0, 0, 0, 0x00, 0x00, 0xFF, 0x00])),
        ("G right-half", bytes([0x33, 0x05, 0x0B, 0, 0, 0, 0x00, 0x00, 0x00, 0x7F])),
        ("H no-mask", bytes([0x33, 0x05, 0x15, 0x01, 0, 0, 0])),
        ("A-twice", bytes([0x33, 0x05, 0x15, 0x01, 0, 0, 0, 0x00, 0x00, 0xFF, 0x7F])),
        ("mode-select-then-A", None),  # special
        ("gradientOff-then-A", None),  # special
        ("scene-then-A", None),  # special
    ]
    return P


def fill_rgb(payload: bytes, rgb: bytes) -> bytes:
    p = bytearray(payload)
    if len(p) >= 7 and p[0] == 0x33 and p[1] == 0x05:
        p[4], p[5], p[6] = rgb[0], rgb[1], rgb[2]
    return bytes(p)


def snap(cap):
    for _ in range(4):
        cap.read()
    ok, f = cap.read()
    return f if ok else None


def green_score(img) -> float:
    """Mean (G - R) over bright pixels in top 45% of frame (strip zone)."""
    h = img.shape[0]
    roi = img[: int(h * 0.45)]
    b, g, r = roi[:, :, 0].astype(float), roi[:, :, 1].astype(float), roi[:, :, 2].astype(float)
    vmax = np.maximum(np.maximum(r, g), b)
    mask = vmax > 70
    if mask.sum() < 500:
        return 0.0
    return float(np.mean(g[mask] - r[mask]))


async def main(mac: str, cam: int):
    # NOTE: BLE *must* connect before the camera is opened — the DirectShow
    # driver flips this thread to STA and WinRT BLE then refuses to work.
    async with BleakClient(mac, timeout=20) as c:
        await asyncio.sleep(0.3)

        async def send(payload: bytes):
            await c.write_gatt_char(WRITE, frame(payload), response=False)

        await send(bytes([0x33, 0x01, 0x01]))
        await asyncio.sleep(0.4)
        await send(bytes([0x33, 0x04, 100]))
        await asyncio.sleep(1.0)

        cap = cv2.VideoCapture(cam, cv2.CAP_DSHOW)
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
        for _ in range(8):
            cap.read()

        base = snap(cap)
        base_score = green_score(base)
        print(f"BASELINE green_score={base_score:.1f} (strip should be red => negative)", flush=True)
        cv2.imwrite("fuzz_baseline.jpg", base)

        variants = color_variants()
        for i, (label, payload) in enumerate(variants, 1):
            print(f"STEP {i}: {label}", flush=True)
            if payload is not None:
                if label == "A-twice":
                    await send(fill_rgb(payload, GREEN))
                    await asyncio.sleep(0.6)
                    await send(fill_rgb(payload, GREEN))
                else:
                    await send(fill_rgb(payload, GREEN))
            else:
                if label.startswith("mode-select"):
                    await send(bytes([0x33, 0x05, 0x15]))
                    await asyncio.sleep(1.0)
                    await send(fill_rgb(variants[0][1], GREEN))
                elif label.startswith("gradientOff"):
                    await send(bytes([0x33, 0xA3, 0x00]))
                    await asyncio.sleep(1.0)
                    await send(fill_rgb(variants[0][1], GREEN))
                elif label.startswith("scene"):
                    await send(bytes([0x33, 0x05, 0x04, 0x00]))
                    await asyncio.sleep(2.0)
                    await send(fill_rgb(variants[0][1], GREEN))
            await c.write_gatt_char(WRITE, KEEP_ALIVE, response=False)
            await asyncio.sleep(3.0)
            img = snap(cap)
            s = green_score(img)
            changed = s - base_score
            print(f"  score={s:.1f} delta={changed:+.1f}", flush=True)
            cv2.imwrite(f"fuzz_step{i}.jpg", img)
            if changed > 25:
                print(f"*** WINNER: step {i} ({label}) delta={changed:+.1f} ***", flush=True)
                cap.release()
                return
        print("no winner — will dump scores for analysis", flush=True)
        cap.release()


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--mac", required=True)
    ap.add_argument("--cam", type=int, default=0)
    a = ap.parse_args()
    asyncio.run(main(a.mac, a.cam))
