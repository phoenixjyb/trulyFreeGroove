# OpenGroove

OpenGroove is a small Android music-discovery and playlist app designed around a simple rule: a track is played in-app only when its provider explicitly supplies an authorized stream and a license reference. Major commercial platforms are opened through their official app or website.

## What works

- Search and stream public-domain and Creative Commons audio from Wikimedia Commons without an API key.
- Optionally expand search with Creative Commons music from Jamendo.
- Filter discovery by English, Chinese, or Cantonese. Cantonese catalog matching is explicitly heuristic because Jamendo exposes `zh` but not a separate Cantonese language code.
- See the provider, source page, and license for every playable result.
- Search YouTube Music, YouTube, Spotify, QQ Music, or NetEase Cloud Music through their official web/app experience.
- Create local playlists and add or remove tracks.
- Play, pause, and seek within an authorized Jamendo stream.
- Search internet radio stations by name, browse by country, genre, or category, and filter out stations reported as broken.
- Save favorite stations locally and use a dedicated live-radio player with previous/next station switching.

The app intentionally has no YouTube downloader, stream extractor, hidden player, or offline cache.

## Internet radio

Radio discovery uses the community-run [Radio Browser](https://www.radio-browser.info/) directory. Station entries include their resolved public stream URL, country, language, tags, codec, and bitrate. OpenGroove reports station plays through Radio Browser's click endpoint and never records or downloads a stream.

Some legacy stations still publish HTTP-only streams, so Android cleartext playback is enabled for radio compatibility. OpenGroove sends its own directory and catalog requests over HTTPS and does not transmit account credentials.

Radio Browser indexes public station URLs but does not grant rights to rebroadcast or record them. OpenGroove acts as a tuner: streams remain under each station's control and can disappear or be geographically restricted.

## Build

Requirements: JDK 17 and Android SDK 35.

1. Create a Jamendo developer application at <https://devportal.jamendo.com/>.
2. Put the public client ID in your untracked `local.properties` file:

   ```properties
   JAMENDO_CLIENT_ID=your_client_id
   ```

3. Build the debug APK:

   ```bash
   ./gradlew assembleDebug
   ```

Jamendo is optional. Without a client ID, the app still searches and streams license-explicit audio from Wikimedia Commons. No third-party test credential is bundled.

## Provider contract

`Track.isDirectPlaybackAllowed()` is the fail-closed playback boundary. A direct stream must:

- be marked `DIRECT_AUTHORIZED` by its provider adapter;
- use HTTPS; and
- include a license URL.

YouTube Music, YouTube, Spotify, and similar services should remain `EXTERNAL_ONLY` unless their official SDK and current terms explicitly grant in-app playback for the intended use. Non-commercial distribution does not waive copyright or provider terms.

## Before sharing the APK

- Register your own Jamendo client ID and review its current API terms.
- Add a privacy notice if analytics, accounts, or cloud sync are introduced.
- Re-check each provider's current branding, attribution, caching, and playback requirements.
- Do not assume that a search result is public-domain; preserve the displayed per-track license and attribution.
