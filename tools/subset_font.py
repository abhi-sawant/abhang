"""Shrinks the bundled Google Sans (4.97 MB) to the glyphs the app uses (about 220 KB).

Needs Python with fonttools (pip install fonttools). Usage, from the repo root:
    python tools/subset_font.py design/backup/google_sans.original.ttf app/src/main/res/font/google_sans.ttf

What it does:
  1. Pins the `opsz` and `GRAD` axes (the app only varies weight), keeping `wght` 400-700.
  2. Keeps Basic Latin, the Devanagari block, and the few punctuation marks the UI and corpus use,
     with the default layout features so Devanagari conjuncts and marks still shape correctly.
If a new character appears in the corpus or strings, add its code point to UNICODES and rerun.
"""
import subprocess, sys, tempfile, os

UNICODES = ("U+0020-007E,U+00A0,U+00B7,U+0900-097F,U+200B-200D,U+2013-2014,U+2018-2019,"
            "U+2022,U+2026,U+2060,U+2192,U+2212,U+2605,U+25CC")

def main(src, dst):
    with tempfile.TemporaryDirectory() as tmp:
        pinned = os.path.join(tmp, "pinned.ttf")
        subprocess.check_call([sys.executable, "-m", "fontTools.varLib.instancer", src,
                               "opsz=18", "GRAD=0", "wght=400:700", "-o", pinned])
        subprocess.check_call([sys.executable, "-m", "fontTools.subset", pinned,
                               f"--unicodes={UNICODES}", "--notdef-outline", "--name-IDs=*",
                               "--layout-features+=kern", "--no-hinting", f"--output-file={dst}"])
    print(dst, os.path.getsize(dst), "bytes")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(*sys.argv[1:])
