#!/usr/bin/env python3
"""Generate a tagged Navidrome test library plus a separate two-track CUE image."""
from __future__ import annotations
import argparse
import subprocess
from pathlib import Path

def encode(ffmpeg: str, output: Path, frequency: int, duration: int, codec: list[str], tags: list[str]) -> None:
    subprocess.run([ffmpeg, "-nostdin", "-v", "error", "-f", "lavfi", "-i", f"sine=frequency={frequency}:duration={duration}", *tags, *codec, "-n", str(output)], check=True)

def main() -> int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("destination", type=Path)
    p.add_argument("--ffmpeg", default="ffmpeg")
    p.add_argument("--duration", type=int, default=65, help="duration of each main test track in seconds")
    p.add_argument("--formats", action="store_true", help="also generate short samples for common codecs and normalization inputs when FFmpeg encoders are available")
    a=p.parse_args()
    root=a.destination.resolve()
    if root.exists() and any(root.iterdir()):
        p.error(f"destination must be empty: {root}")
    music=root/"music"/"Fixture Artist"/"Fixture Album"
    music.mkdir(parents=True,exist_ok=True)
    for num,title,freq,ext,codec in [
        (1,"Fixture MP3",440,"mp3",["-c:a","libmp3lame","-b:a","192k"]),
        (2,"Fixture FLAC",660,"flac",["-c:a","flac"]),
    ]:
        dst=music/f"{num:02d} - {title}.{ext}"
        tags=["-metadata",f"title={title}","-metadata","artist=Fixture Artist","-metadata","album=Fixture Album","-metadata","album_artist=Fixture Artist","-metadata",f"track={num}"]
        encode(a.ffmpeg,dst,freq,a.duration,codec,tags)
    cue_dir=root/"cue-source"; cue_dir.mkdir(parents=True,exist_ok=True)
    encode(a.ffmpeg,cue_dir/"disc.flac",550,4,["-c:a","flac"],["-metadata","title=CUE test image"])
    (cue_dir/"disc.cue").write_text('TITLE "CUE Fixture Album"\nPERFORMER "Fixture Artist"\nFILE "disc.flac" WAVE\n TRACK 01 AUDIO\n TITLE "CUE One"\n INDEX 01 00:00:00\n TRACK 02 AUDIO\n TITLE "CUE Two"\n INDEX 01 00:02:00\n',encoding="utf-8")
    print(f"Generated tagged MP3/FLAC fixture in {music} and two-track CUE source in {cue_dir}.")
    print(f"Split the CUE into a separate MusicFolder path with: python prepare_library.py cue \"{cue_dir / 'disc.cue'}\" \"{root / 'cue-prepared'}\"")
    if a.formats:
        result=subprocess.run([a.ffmpeg,"-hide_banner","-encoders"],capture_output=True,text=True,check=True)
        encoders=result.stdout + result.stderr
        variants=[("wav","sample.wav",["-c:a","pcm_s16le"]),("aac_m4a","sample.m4a",["-c:a","aac","-b:a","128k"]),
                  ("alac_m4a","sample.m4a",["-c:a","alac"]),("ogg_vorbis","sample.ogg",["-c:a","vorbis","-strict","-2","-ac","2"]),
                  ("opus","sample.opus",["-c:a","libopus","-b:a","96k"]),("wma","sample.wma",["-c:a","wmav2","-b:a","96k"]),
                  ("raw_aac","sample.aac",["-c:a","aac","-b:a","128k"]),("aiff","sample.aiff",["-c:a","pcm_s16be"])]
        for label,filename,codec in variants:
            encoder=codec[1]
            if encoder not in encoders:
                print(f"Skipped {label}: FFmpeg encoder {encoder} unavailable")
                continue
            folder=root/"format-fixtures"/label; folder.mkdir(parents=True,exist_ok=True)
            tags=["-metadata",f"title={label} test","-metadata",f"artist={label} Artist","-metadata",f"album={label} Album","-metadata",f"album_artist={label} Artist"]
            encode(a.ffmpeg,folder/filename,520,8,codec,tags)
        print(f"Generated optional codec samples under {root / 'format-fixtures'}.")
    return 0

if __name__ == "__main__": raise SystemExit(main())
