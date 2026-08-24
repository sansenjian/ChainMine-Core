#!/usr/bin/env python3
"""Generate 512x512 mod icon for Modrinth (red diamond, scaled from the 16x16 design)."""
import zlib
import struct
import os

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "docs", "modrinth")
SIZE = 512


def write_png(path, w, h, pixels):
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        c += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return c

    raw = b""
    for y in range(h):
        raw += b"\x00"
        for x in range(w):
            r, g, b, a = pixels[y * w + x]
            raw += bytes((r, g, b, a))
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def icon_pixel(x, y):
    """Red diamond with dark outline + white glint, on transparent background."""
    cx, cy = SIZE / 2.0, SIZE / 2.0
    max_d = int(SIZE * 0.4375)  # 7/16 * 512 = 224
    d = abs(x - cx) + abs(y - cy)
    if d > max_d:
        return (0, 0, 0, 0)
    # dark outline on the outer ~6%
    if d >= max_d * 0.86:
        t = (d - max_d * 0.86) / (max_d * 0.14)
        base = (int(120 - 40 * t), 20, 25, 255)
        return base
    t = d / max_d
    r = int(235 - 110 * t)
    g = int(45 + 35 * t)
    b = int(50 + 15 * t)
    # white glint upper-left
    glint = abs((x - SIZE * 0.28) + (y - SIZE * 0.28))
    if glint <= SIZE * 0.11 and d <= max_d * 0.72:
        return (255, 255, 255, 245)
    return (r, g, b, 255)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, "icon.png")
    write_png(path, SIZE, SIZE,
              [icon_pixel(x, y) for y in range(SIZE) for x in range(SIZE)])
    print(f"OK: {path}")


if __name__ == "__main__":
    main()
