"""把 incoming/ 里刚截的手机截批量套上机身壳。跑法：python docs/tools/frame_batch.py"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from frame_ui import IMG, frame_phone  # noqa: E402

JOBS = [
    ("zh-main-dark", "ui-android-main-dark-device"),
    ("zh-settings-dark", "ui-android-settings-dark-device"),
    ("zh-about-dark", "ui-android-about-dark-device"),
    ("en-main-light", "ui-android-main-en-light-device"),
    ("en-about-light", "ui-android-about-en-light-device"),
    ("en-settings-dark", "ui-android-settings-en-dark-device"),
    ("en-about-dark", "ui-android-about-en-dark-device"),
]

def main() -> int:
    src_dir = IMG / "incoming"
    for src, dst in JOBS:
        p = src_dir / f"{src}.png"
        if not p.exists():
            print(f"跳过（缺文件）{src}.png")
            continue
        frame_phone(p, IMG / f"{dst}.png")
    return 0

if __name__ == "__main__":
    sys.exit(main())
