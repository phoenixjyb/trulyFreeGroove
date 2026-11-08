"""Exercise generated audio through the preparation CLIs; no real library required."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


def run(*args: str) -> None:
    subprocess.run(args, check=True)


def probe(path: Path) -> dict:
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", str(path)],
        check=True, capture_output=True, text=True,
    )
    return json.loads(result.stdout)


def main() -> None:
    for tool in ("ffmpeg", "ffprobe"):
        if not shutil.which(tool):
            raise SystemExit(f"Required audio tool missing: {tool}")
    repo = Path(__file__).resolve().parents[2]
    generator = str(repo / "server/windows/generate_fixture.py")
    preparer = str(repo / "server/windows/prepare_library.py")
    with tempfile.TemporaryDirectory(prefix="opengroove-audio-") as folder:
        root = Path(folder) / "fixture"
        run(sys.executable, generator, str(root), "--duration", "2", "--formats")
        originals = {p: hashlib.sha256(p.read_bytes()).digest() for p in root.rglob("*") if p.is_file()}
        run(sys.executable, preparer, "stage", str(root / "music"), str(root / "staged"))
        assert len(list((root / "staged").rglob("*.mp3"))) == 1
        assert len(list((root / "staged").rglob("*.flac"))) == 1
        run(sys.executable, preparer, "cue", str(root / "cue-source/disc.cue"), str(root / "split"))
        splits = sorted((root / "split").glob("*.flac"))
        assert len(splits) == 2
        for path, title in zip(splits, ("CUE One", "CUE Two")):
            info = probe(path)
            assert abs(float(info["format"]["duration"]) - 2.0) < 0.05
            assert info["format"]["tags"]["title"] == title
            assert info["format"]["tags"]["album"] == "CUE Fixture Album"
        for label, extension, target_codec in (("raw_aac", "aac", "aac"), ("aiff", "aiff", "flac")):
            source = root / "format-fixtures" / label / f"sample.{extension}"
            if not source.exists():
                raise SystemExit(f"Required normalization fixture was not generated: {label}")
            output = root / "normalized" / label
            run(sys.executable, preparer, "normalize", str(source), str(output),
                "--title", label, "--artist", "Fixture", "--album", "Normalized")
            prepared = next(output.iterdir())
            info = probe(prepared)
            assert info["streams"][0]["codec_name"] == target_codec
            assert info["format"]["tags"]["title"] == label
        for source, digest in originals.items():
            assert hashlib.sha256(source.read_bytes()).digest() == digest, "Original fixture changed"
        audio = [p for p in root.rglob("*") if p.is_file() and p.suffix != ".cue"]
        for path in audio:
            run("ffmpeg", "-nostdin", "-v", "error", "-i", str(path), "-map", "0:a:0", "-f", "null", "-")
        print(f"Audio smoke passed: {len(audio)} files decoded; CUE boundaries/tags and AAC/AIFF normalization verified; originals preserved.")


if __name__ == "__main__":
    main()
