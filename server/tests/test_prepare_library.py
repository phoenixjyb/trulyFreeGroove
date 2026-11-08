import tempfile
import unittest
import tomllib
from pathlib import Path
from unittest.mock import patch

from server.windows.prepare_library import normalize, parse_cue, stage


class LibraryPrepTests(unittest.TestCase):
    def test_stage_copies_supported_audio_and_preserves_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "source"
            source.mkdir()
            (source / "album").mkdir()
            for ext in ("mp3", "FLAC", "wav", "m4a", "ape", "ogg", "oga", "opus", "wma"):
                (source / "album" / f"song.{ext}").write_bytes(b"audio")
            (source / "album" / "notes.txt").write_text("skip")
            dest = root / "staged"
            self.assertEqual(stage(source, dest), (9, 0))
            self.assertTrue((source / "album" / "song.FLAC").exists())
            self.assertTrue((dest / "album" / "song.FLAC").exists())
            self.assertFalse((dest / "album" / "notes.txt").exists())
            self.assertFalse((dest / "album" / "song.aac").exists())
            self.assertEqual(stage(source, dest), (0, 0))

    def test_stage_rejects_unnormalized_audio_before_copying(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); source = root / "source"; source.mkdir()
            (source / "good.flac").touch()
            (source / "raw.aiff").touch()
            destination = root / "prepared"
            with self.assertRaisesRegex(ValueError, "normalize"):
                stage(source, destination)
            self.assertFalse(destination.exists())

    def test_parse_cue_resolves_relative_image_and_track_boundaries(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "disc.flac").touch()
            cue = root / "disc.cue"
            cue.write_text('TITLE "Album"\nPERFORMER "Artist"\nFILE "disc.flac" WAVE\n TRACK 01 AUDIO\n TITLE "First"\n INDEX 01 00:00:00\n TRACK 02 AUDIO\n TITLE "Second"\n INDEX 01 00:02:00\n')
            audio, tracks, album, artist = parse_cue(cue)
            self.assertEqual(audio.name, "disc.flac")
            self.assertEqual((album, artist), ("Album", "Artist"))
            self.assertEqual([t["title"] for t in tracks], ["First", "Second"])
            self.assertEqual(tracks[1]["start"], 2)

    def test_rejects_cue_with_multiple_files_missing_index_or_pregap(self):
        cases = [
            'TITLE "A"\nFILE "a.flac" WAVE\nFILE "b.flac" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\n',
            'TITLE "A"\nFILE "a.flac" WAVE\nTRACK 01 AUDIO\n',
            'TITLE "A"\nFILE "a.flac" WAVE\nTRACK 01 AUDIO\nPREGAP 00:02:00\nINDEX 01 00:00:00\n',
        ]
        with tempfile.TemporaryDirectory() as tmp:
            cue = Path(tmp) / "bad.cue"
            for body in cases:
                cue.write_text(body)
                with self.assertRaises(ValueError):
                    parse_cue(cue)

    def test_stage_rejects_cue_and_overlapping_trees(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "source"
            source.mkdir()
            (source / "disc.cue").write_text('FILE "disc.flac" WAVE')
            with self.assertRaises(ValueError):
                stage(source, root / "dest")
            (source / "disc.cue").unlink()
            with self.assertRaises(ValueError):
                stage(source, root)

    def test_rejects_symlinks_and_cue_path_escape(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); source = root / "source"; source.mkdir()
            outside = root / "outside"; outside.mkdir(); (outside / "x.mp3").touch()
            (source / "link").symlink_to(outside, target_is_directory=True)
            with self.assertRaises(ValueError): stage(source, root / "dest")
            (source / "link").unlink()
            (source / "disc.cue").write_text('TITLE "A"\nFILE "../outside.flac" WAVE\nTRACK 01 AUDIO\nINDEX 01 00:00:00\n')
            with self.assertRaises(ValueError): parse_cue(source / "disc.cue")

    def test_windows_config_uses_literal_single_backslashes(self):
        config = tomllib.loads(Path("server/windows/navidrome.example.toml").read_text())
        self.assertEqual(config["MusicFolder"], "C:\\OpenGroove\\Music")
        self.assertEqual(config["FFmpegPath"], "C:\\ffmpeg\\bin\\ffmpeg.exe")

    def test_normalize_selects_aac_copy_or_aiff_flac_and_preserves_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); src=root/"inputs"; src.mkdir(); dst=root/"prepared"
            aac=src/"raw.aac"; aac.write_bytes(b"raw aac")
            with patch("server.windows.prepare_library.subprocess.run") as run:
                out=normalize(aac,dst,"AAC Title","Artist","Album","ffmpeg")
                self.assertEqual(out.name,"raw.m4a")
                self.assertIn("copy",run.call_args.args[0])
                self.assertEqual(aac.read_bytes(),b"raw aac")
            aiff=src/"raw.aiff"; aiff.write_bytes(b"aiff")
            with patch("server.windows.prepare_library.subprocess.run") as run:
                out=normalize(aiff,dst,"AIFF Title","Artist","Album","ffmpeg")
                self.assertEqual(out.name,"raw.flac")
                self.assertIn("flac",run.call_args.args[0])
                out.touch()
            with self.assertRaises(FileExistsError):
                normalize(aiff,dst,"AIFF Title","Artist","Album","ffmpeg")


if __name__ == "__main__":
    unittest.main()
