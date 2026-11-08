#!/usr/bin/env python3
"""Build a Navidrome-ready library without modifying the source collection."""
from __future__ import annotations
import argparse
import hashlib
import re
import shutil
import subprocess
import sys
from pathlib import Path

AUDIO_EXTENSIONS = {".mp3", ".flac", ".wav", ".m4a", ".ape", ".ogg", ".oga", ".opus", ".wma"}

def is_link_or_junction(path: Path) -> bool:
    return path.is_symlink() or (getattr(path, "is_junction", lambda: False)())


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def stage(source: Path, destination: Path) -> tuple[int, int]:
    if is_link_or_junction(source) or is_link_or_junction(destination):
        raise ValueError("Source and destination roots must not be symlinks or junctions")
    source, destination = source.resolve(), destination.resolve()
    if not source.is_dir():
        raise ValueError(f"Source folder does not exist: {source}")
    if source == destination or source in destination.parents or destination in source.parents:
        raise ValueError("Source and destination must be separate, non-overlapping trees")
    if any(is_link_or_junction(p) for p in source.rglob("*")):
        raise ValueError("Source contains a symlink or junction; stage real folders only")
    if destination.exists() and any(is_link_or_junction(p) for p in destination.rglob("*")):
        raise ValueError("Destination contains a symlink or junction")
    if any(p.suffix.lower() == ".cue" for p in source.rglob("*")):
        raise ValueError("Source contains CUE sheets; convert whole-disc albums separately before staging")
    if any(p.suffix.lower() in {".aac", ".aif", ".aiff"} for p in source.rglob("*") if p.is_file()):
        raise ValueError("Standalone AAC/AIFF inputs require the normalize command before staging; none were copied")
    destination.mkdir(parents=True, exist_ok=True)
    source_audio = {p.relative_to(source) for p in source.rglob("*") if p.is_file() and p.suffix.lower() in AUDIO_EXTENSIONS}
    copied = 0
    for rel in source_audio:
        original, target = source / rel, destination / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            if digest(original) != digest(target):
                raise ValueError(f"Staged file differs from source; review and replace manually: {target}")
        else:
            shutil.copy2(original, target)
            copied += 1
    stale = sum(1 for p in destination.rglob("*") if p.is_file() and p.suffix.lower() in AUDIO_EXTENSIONS and p.relative_to(destination) not in source_audio)
    return copied, stale

def parse_cue(cue_path: Path) -> tuple[Path, list[dict[str, str | int | float]], str, str]:
    if is_link_or_junction(cue_path):
        raise ValueError("CUE file must not be a symlink or junction")
    raw = cue_path.read_bytes()
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        text = raw.decode("gb18030")
    files: list[tuple[str, str]] = []
    tracks: list[dict[str, str | int | float]] = []
    cur: dict[str, str | int | float] | None = None
    album = performer = ""
    unsupported: list[str] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        m = re.match(r'^FILE\s+"?(.+?)"?\s+(\w+)$', line, re.I)
        if m:
            files.append((m.group(1).strip('"'), m.group(2).upper()))
            continue
        m = re.match(r"^TRACK\s+(\d+)\s+(\w+)", line, re.I)
        if m:
            if m.group(2).upper() != "AUDIO": unsupported.append("non-AUDIO tracks")
            cur = {"number": int(m.group(1)), "title": "", "performer": "", "start": -1.0}
            tracks.append(cur)
            continue
        m = re.match(r'^TITLE\s+"(.*)"$', line, re.I)
        if m:
            if cur is None: album = m.group(1)
            else: cur["title"] = m.group(1)
            continue
        m = re.match(r'^PERFORMER\s+"(.*)"$', line, re.I)
        if m:
            if cur is None: performer = m.group(1)
            else: cur["performer"] = m.group(1)
            continue
        if re.match(r"^(INDEX\s+00|PREGAP|POSTGAP)\b", line, re.I):
            unsupported.append("pregap/INDEX 00")
            continue
        m = re.match(r"^INDEX\s+(\d+)\s+(\d+):(\d+):(\d+)$", line, re.I)
        if m:
            if cur is None: unsupported.append("INDEX outside track")
            elif int(m.group(1)) != 1: unsupported.append(f"INDEX {m.group(1)}")
            else:
                mm, ss, ff = map(int, m.groups()[1:])
                if ss >= 60 or ff >= 75: unsupported.append("invalid INDEX time")
                else: cur["start"] = mm * 60 + ss + ff / 75
    if unsupported:
        raise ValueError("Unsupported CUE: " + ", ".join(sorted(set(unsupported))))
    if len(files) != 1 or files[0][1] == "BINARY" or not tracks or not album:
        raise ValueError("Supported CUE requires one non-BINARY FILE, album TITLE, and AUDIO tracks")
    if any(t["start"] < 0 for t in tracks): raise ValueError("Every AUDIO track needs INDEX 01")
    if [int(t["number"]) for t in tracks] != list(range(1, len(tracks) + 1)):
        raise ValueError("Track numbers must be sequential starting at 1")
    if any(a["start"] >= b["start"] for a, b in zip(tracks, tracks[1:])):
        raise ValueError("CUE track start times must be strictly increasing")
    ref = Path(files[0][0])
    if ref.is_absolute() or ref.drive or ".." in ref.parts:
        raise ValueError("CUE image must be an adjacent relative file")
    cue_dir = cue_path.parent.resolve()
    audio = (cue_dir / ref).resolve()
    if audio.parent != cue_dir or is_link_or_junction(cue_dir / ref):
        raise ValueError("CUE image must be an adjacent regular file")
    if not audio.is_file(): raise ValueError(f"CUE audio file not found: {audio}")
    return audio, tracks, album, performer

def convert_cue(cue_path: Path, destination: Path, ffmpeg: str) -> int:
    audio, tracks, album, album_artist = parse_cue(cue_path)
    source_root = cue_path.parent.resolve()
    if is_link_or_junction(destination): raise ValueError("Output folder must not be a symlink or junction")
    resolved_destination = destination.resolve()
    if resolved_destination == source_root or resolved_destination in source_root.parents or source_root in resolved_destination.parents:
        raise ValueError("CUE source and output must be separate, non-overlapping trees")
    destination = resolved_destination
    destination.mkdir(parents=True, exist_ok=True)
    if any(is_link_or_junction(p) for p in destination.rglob("*")):
        raise ValueError("Output tree contains a symlink or junction")
    for i, track in enumerate(tracks):
        start = float(track["start"])
        end = float(tracks[i + 1]["start"]) if i + 1 < len(tracks) else None
        title = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", str(track["title"])).strip(" .") or "Track"
        target = destination / f"{int(track['number']):02d} - {title}.flac"
        if target.exists() or is_link_or_junction(target): raise FileExistsError(f"Refusing to overwrite existing output: {target}")
        args = [ffmpeg, "-nostdin", "-v", "error", "-ss", str(start), "-i", str(audio)]
        if end is not None: args += ["-t", str(end - start)]
        args += ["-map", "0:a:0", "-c:a", "flac", "-metadata", f"track={track['number']}", "-metadata", f"title={track['title'] or title}", "-metadata", f"album={album}"]
        args += ["-metadata", f"artist={track['performer'] or album_artist}", "-metadata", f"album_artist={album_artist}", "-n", str(target)]
        subprocess.run(args, check=True)
    return len(tracks)

def normalize(source: Path, destination: Path, title: str, artist: str, album: str, ffmpeg: str) -> Path:
    if is_link_or_junction(source) or not source.is_file():
        raise ValueError("Input must be a regular, non-symlink audio file")
    source = source.resolve()
    destination = destination.expanduser()
    if is_link_or_junction(destination):
        raise ValueError("Output folder must not be a symlink or junction")
    destination = destination.resolve()
    if destination == source.parent or destination in source.parents or source.parent in destination.parents:
        raise ValueError("Normalization input and output must be separate, non-overlapping trees")
    suffix = source.suffix.lower()
    if suffix == ".aac":
        output_name, codec = source.stem + ".m4a", "copy"
    elif suffix in {".aif", ".aiff"}:
        output_name, codec = source.stem + ".flac", "flac"
    else:
        raise ValueError("normalize supports standalone .aac to .m4a and .aif/.aiff to FLAC")
    destination.mkdir(parents=True, exist_ok=True)
    if any(is_link_or_junction(p) for p in destination.rglob("*")):
        raise ValueError("Output tree contains a symlink or junction")
    target = destination / output_name
    if target.exists() or is_link_or_junction(target):
        raise FileExistsError(f"Refusing to overwrite existing output: {target}")
    args = [ffmpeg, "-nostdin", "-v", "error", "-i", str(source), "-map", "0:a:0", "-c:a", codec,
            "-metadata", f"title={title}", "-metadata", f"artist={artist}", "-metadata", f"album={album}",
            "-metadata", f"album_artist={artist}", "-n", str(target)]
    subprocess.run(args, check=True)
    return target

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    st = sub.add_parser("stage"); st.add_argument("source", type=Path); st.add_argument("destination", type=Path)
    cue = sub.add_parser("cue"); cue.add_argument("cue", type=Path); cue.add_argument("destination", type=Path); cue.add_argument("--ffmpeg", default="ffmpeg")
    norm = sub.add_parser("normalize", help="wrap raw AAC as M4A or convert AIFF to FLAC")
    norm.add_argument("source", type=Path); norm.add_argument("destination", type=Path)
    norm.add_argument("--title", required=True); norm.add_argument("--artist", required=True); norm.add_argument("--album", required=True)
    norm.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args()
    try:
        if args.command == "stage":
            count, stale = stage(args.source, args.destination)
            print(f"Copied {count} file(s). Source files were not modified.")
            if stale: print(f"WARNING: destination contains {stale} audio file(s) absent from source; review manually (nothing deleted).", file=sys.stderr)
        elif args.command == "cue": print(f"Prepared {convert_cue(args.cue, args.destination, args.ffmpeg)} FLAC track(s). Original image/CUE preserved.")
        else: print(f"Prepared {normalize(args.source, args.destination, args.title, args.artist, args.album, args.ffmpeg)}. Original preserved.")
    except (OSError, UnicodeError, ValueError, subprocess.CalledProcessError) as exc:
        print(f"error: {exc}", file=sys.stderr); return 1
    return 0

if __name__ == "__main__": raise SystemExit(main())
