#!/usr/bin/env python3
"""Generate ChainMine placeholder textures (ruby item + mod icon), 16x16 RGBA PNG."""
import zlib
import struct
import os

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "chainmine", "textures", "item")
ICON_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "chainmine")


def write_png(path, w, h, pixels):
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        c += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return c

    raw = b""
    for y in range(h):
        raw += b"\x00"  # filter: None
        for x in range(w):
            r, g, b, a = pixels[y * w + x]
            raw += bytes((r, g, b, a))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def ruby_pixel(x, y):
    """Red diamond gem with top highlight, on transparent background."""
    cx, cy = 7.5, 7.5
    d = abs(x - cx) + abs(y - cy)
    if d > 7.0:
        return (0, 0, 0, 0)
    # gradient: core bright red -> edge dark red
    t = d / 7.0
    r = int(255 - 130 * t)
    g = int(50 + 20 * t)
    b = int(50 + 10 * t)
    a = 255
    # top-left highlight facet
    if (x <= 5 and y <= 5 and d <= 5) or (x <= 3 and y <= 9 and d >= 3 and d <= 6 and x + y <= 10):
        r = min(255, r + 90)
        g = min(255, g + 60)
        b = min(255, b + 60)
    # white glint on upper-left
    if abs((x - 4.5) + (y - 4.5)) <= 1.6 and d <= 5.5:
        return (255, 255, 255, 235)
    return (r, g, b, a)


def icon_pixel(x, y):
    """Chunkier red diamond for the mod icon (border + inner)."""
    cx, cy = 7.5, 7.5
    d = abs(x - cx) + abs(y - cy)
    if d > 7.0:
        return (0, 0, 0, 0)
    if d >= 6.0:
        # dark outline
        return (120, 20, 25, 255)
    t = d / 7.0
    r = int(230 - 100 * t)
    g = int(40 + 30 * t)
    b = int(45 + 15 * t)
    if abs((x - 4.5) + (y - 4.5)) <= 1.8 and d <= 5.0:
        return (255, 255, 255, 245)
    return (r, g, b, 255)


def main():
    size = 16
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(ICON_DIR, exist_ok=True)

    write_png(os.path.join(OUT_DIR, "ruby.png"), size, size,
              [ruby_pixel(x, y) for y in range(size) for x in range(size)])
    write_png(os.path.join(ICON_DIR, "icon.png"), size, size,
              [icon_pixel(x, y) for y in range(size) for x in range(size)])
    print("OK: ruby.png + icon.png generated")


if __name__ == "__main__":
    main()
