#!/usr/bin/env python3
"""Assemble an .icns file from an .iconset directory.

macOS builds this with `iconutil -c icns`, which only exists on macOS; this script
produces the same PNG-backed container so the icon can also be rebuilt on Linux.

Usage: python3 scripts/make_icns.py packaging/AdaGIDE.iconset packaging/AdaGIDE.icns
"""

import struct
import sys
from pathlib import Path

# icns chunk type -> the iconset file that provides its pixels.
ENTRIES = [
    ("icp4", "icon_16x16.png"),
    ("icp5", "icon_32x32.png"),
    ("ic11", "icon_16x16@2x.png"),
    ("ic12", "icon_32x32@2x.png"),
    ("ic07", "icon_128x128.png"),
    ("ic13", "icon_128x128@2x.png"),
    ("ic08", "icon_256x256.png"),
    ("ic14", "icon_256x256@2x.png"),
    ("ic09", "icon_512x512.png"),
    ("ic10", "icon_512x512@2x.png"),
]


def build(iconset: Path, target: Path) -> None:
    chunks = []
    for chunk_type, file_name in ENTRIES:
        source = iconset / file_name
        if not source.is_file():
            raise SystemExit(f"missing {source}")
        payload = source.read_bytes()
        chunks.append(chunk_type.encode("ascii") + struct.pack(">I", len(payload) + 8) + payload)

    body = b"".join(chunks)
    target.write_bytes(b"icns" + struct.pack(">I", len(body) + 8) + body)
    print(f"{target} ({target.stat().st_size} bytes, {len(chunks)} images)")


if __name__ == "__main__":
    iconset_path = Path(sys.argv[1] if len(sys.argv) > 1 else "packaging/AdaGIDE.iconset")
    icns_path = Path(sys.argv[2] if len(sys.argv) > 2 else "packaging/AdaGIDE.icns")
    build(iconset_path, icns_path)
