# Home Music server preparation

## Recommendation and Android contract

Use Navidrome on the Windows home machine. Its current documentation lists
Windows binaries/MSI and documents Subsonic API 1.16.1 compatibility, including
`ping`, `getAlbumList2`, `getAlbum`, `search3`, and `stream`. The Android client
can call `/rest/{method}.view` with `u`, `t`, `s`, `v=1.16.1`, `c=OpenGroove`,
and `f=json`. Use Subsonic token authentication (`t` and `s`); never put the
account password in app preferences or logs. IDs must remain strings.

For predictable Android playback, request `stream` with `format=mp3` and
`maxBitRate=192` so Navidrome transcodes source formats through FFmpeg. The app
persists only a logical `home-music://accountKey/id` URI and resolves the
authenticated stream URI at data-source request time. Navidrome's compatibility
page says stream supports transcoding options. A temporary loopback Navidrome
runtime was used to validate these calls against generated fixtures; it is not
the Windows server or real collection.

## Windows setup outline

1. Select a Windows music root (and a separate staging root if normalization is needed). The service account
   must have read access to staged music and write access to Navidrome data and
   cache directories. Do not make the source collection writable to the server.
2. Install FFmpeg for Windows and Navidrome using the official Windows install
   guide. Installation creates a service and starts it, so this step needs
   separate authorization before any Windows changes are made.
3. Copy `server/windows/navidrome.example.toml` next to the executable. Set
   paths to the chosen staging, data, and cache directories. Keep `Address` at
   `127.0.0.1` for local-only setup. To enable home Wi-Fi clients, replace it
   with the Windows machine's stable LAN address (to be supplied later) and
   allow TCP 4533 only on the private home network in Windows Firewall. Do not configure router port
   forwarding or public DNS. Address binding alone does not replace firewall
   policy.
4. Start the server, create a unique account with a strong password in the
   first-run UI, and keep authentication enabled. Create a normal playback user
   for the app instead of sharing the administrator account.
5. Verify from Windows at `http://127.0.0.1:4533`, then from Android on the same
   Wi-Fi using `http://<windows-lan-address>:4533`. Confirm ping, album listing,
   search, and an MP3-transcoded stream before treating device playback as
   accepted.

The Navidrome process must retain access to the selected folders after reboot.
Avoid mapped network drives for a service account unless its logon identity is
configured and verified. Windows MSI installation, service changes, firewall
rules, account creation, library indexing, router settings, and Android device
playback have not been performed here.

## Library formats and CUE albums

Navidrome documents support for MP3, FLAC, AAC, Opus, WMA, APE and other
formats; FFmpeg is required. The prep script stages MP3, FLAC, WAV, M4A, APE,
OGG/OGA, Opus, and WMA. `.m4a` can contain AAC or ALAC; both variants were
indexed and transcoded in the local test. Standalone AAC and AIFF are not
indexed directly in the tested runtime, so normalize before adding them.
Staging refuses these unnormalized inputs with an explicit message before copying. The normalizer wraps AAC in
M4A with stream copy (no audio re-encode), and converts AIFF to tagged FLAC
(lossless). APE is staged based on documented support but was not tested because
this FFmpeg build has no APE encoder. We do not promise WV, TTA, MPC, DSF, or
DFF without checking scanner and decoder support.

Album browsing uses embedded tags; untagged files can appear as Unknown Album
or Unknown Artist. Review tags before indexing the real collection.

Staging is optional: point Navidrome directly at an existing collection folder
with read-only access when its layout is suitable. Use the tool when you need a
separate normalized root for CUE-converted FLACs or a combined tree from several
sources. It preserves originals, rejects source/destination overlap and
symlinks/junctions in either tree, and refuses a CUE-containing source so a
whole image is not indexed beside split tracks. It refuses to silently replace
a staged file whose content changed. Extra staged tracks are reported as stale;
the tool does not delete them. Reconcile stale files manually after review.

Run from the repository root:

```powershell
py -3 server/windows/prepare_library.py stage "D:\Music" "D:\OpenGroove\Music"
```

Whole-disc image plus CUE is not treated as individual playable tracks by this
tool or promised as native Navidrome CUE support. Convert a supported single
image reference into separate tagged FLAC tracks in a separate output folder:

```powershell
py -3 server/windows/prepare_library.py cue "D:\Albums\Disc.cue" "D:\OpenGroove\Music\Artist\Album"
```

Normalize standalone inputs into a separate reviewed library folder, then
point Navidrome at the normalized result or copy it into the staging tree:

```powershell
py -3 server/windows/prepare_library.py normalize "D:\Incoming\track.aac" "D:\OpenGroove\Normalized\AAC" --title "Track" --artist "Artist" --album "Album"
py -3 server/windows/prepare_library.py normalize "D:\Incoming\track.aiff" "D:\OpenGroove\Normalized\AIFF" --title "Track" --artist "Artist" --album "Album"
```

The CUE must reference one adjacent image file and contain AUDIO tracks with
increasing `INDEX 01` times. FFmpeg decodes the source image, so WAV/FLAC/M4A/APE
support depends on that FFmpeg build. Conversion writes FLAC outputs only,
refuses to overwrite existing tracks, and never edits the original image or
CUE. Review boundaries and tags for pregap, hidden-track, multi-file, or unusual
CUE sheets; these cases are not represented by the basic splitter.

Generate a reproducible test library and standalone two-track CUE source:

```powershell
py -3 server/windows/generate_fixture.py "D:\OpenGrooveFixture"
py -3 server/windows/prepare_library.py cue "D:\OpenGrooveFixture\cue-source\disc.cue" "D:\OpenGrooveFixture\cue-prepared"
```

Pass `--formats` to generate additional codec samples when the local FFmpeg
build provides the encoders.

Point Navidrome at the generated `music` folder to index MP3/FLAC; add
`cue-prepared` as a subfolder of a separate test MusicFolder if you want both
tracks indexed. Inspect durations and tags with `ffprobe`. These fixtures prove
local handling, not the real collection's encoding or Windows service setup.

## Format and validation status

| Source | Staging | Navidrome indexing evidence | Stream decode evidence |
|---|---|---|---|
| MP3 | Supported extension | Indexed | Streamed and ffprobe verified as MP3, 192 kbps |
| FLAC | Supported extension | Indexed | Streamed and ffprobe verified as MP3, 192 kbps |
| WAV | Supported extension | Indexed | Streamed and ffprobe verified as MP3, 192 kbps |
| M4A containing AAC | Supported extension | Indexed | Streamed and ffprobe verified as MP3, 128 kbps |
| M4A containing ALAC | Supported extension | Indexed | Streamed and ffprobe verified as MP3, 192 kbps |
| Opus | Supported extension | Indexed | Streamed and ffprobe verified as MP3, 160 kbps |
| WMA | Supported extension | Indexed | Streamed and ffprobe verified as MP3, 128 kbps |
| Standalone AAC normalized to M4A | Normalize command | Indexed | Streamed and ffprobe verified as MP3 |
| AIFF normalized to FLAC | Normalize command | Indexed | Streamed and ffprobe verified as MP3 |
| Standalone `.aac` / `.aif` / `.aiff` | Not staged | `.aac` and `.aiff` not indexed directly | Not tested |
| APE | Supported extension | Not tested; FFmpeg build has no APE encoder | Not tested |
| OGG Vorbis | Supported extension | Indexed | Streamed and ffprobe verified as MP3 |
| Whole-disc+CUE | Not staged directly; explicit conversion required | Generated CUE splitter tested separately | Generated FLAC splits decoded via FFmpeg; no Navidrome indexing test |

## Checks performed here

- `python3 -m unittest server.tests.test_prepare_library` — passed (copy,
  extension selection, source preservation, repeat-run behavior, CUE safety,
  Windows TOML path parsing, and AAC/AIFF normalization command safety).
- A generated four-second FLAC tone was split into two two-second FLAC tracks
  with expected title tags using the installed FFmpeg/ffprobe.
- Navidrome v0.64.2 ran from `/private/tmp` on loopback port 14533 with a
  generated 65-second MP3/FLAC album and nine additional indexed format samples.
  `ping`, `getAlbumList2` (`type=alphabeticalByName`), `getAlbum`, `search3`,
  and `stream` with `format=mp3&maxBitRate=192` passed. MP3, FLAC, WAV, AAC-M4A,
  ALAC-M4A, Opus, WMA, and OGG/Vorbis each indexed and streamed; FFprobe decoded
  every stream as MP3. Raw AAC and AIFF were not indexed directly. Normalized
  AAC-M4A and AIFF-FLAC indexed and streamed. The local FFmpeg build lacks an
  APE encoder, so APE was not tested. This synthetic runtime is separate from
  the Windows host and real collection.
- No real collection or Windows service was changed or validated.

## References

- [Navidrome Windows installation](https://www.navidrome.org/docs/installation/windows/)
- [Navidrome configuration options](https://www.navidrome.org/docs/usage/configuration/options/)
- [Navidrome Subsonic API compatibility](https://www.navidrome.org/docs/developers/subsonic-api/)
- [Navidrome getting started and supported formats](https://www.navidrome.org/docs/getting-started/)
