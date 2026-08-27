# OpenGroove

OpenGroove is a small Android music, internet-radio, and podcast app designed around a simple rule: media is played in-app only from a provider, broadcaster, or publisher URL intended for that playback. Major commercial music platforms are opened through their official app or website.

## What works

- Search and stream public-domain and Creative Commons audio from Wikimedia Commons without an API key.
- Optionally expand search with Creative Commons music from Jamendo.
- Filter discovery by English, Chinese, or Cantonese. Cantonese catalog matching is explicitly heuristic because Jamendo exposes `zh` but not a separate Cantonese language code.
- See the provider, source page, and license for every playable result.
- Search YouTube Music, YouTube, Spotify, QQ Music, or NetEase Cloud Music through their official web/app experience.
- Create local playlists and add or remove tracks.
- Keep playlists, saved/recent stations, podcast subscriptions, episodes, and playback progress in a Room database. Existing preference-backed playlists and radio data migrate automatically once.
- Play, pause, and seek within an authorized Jamendo stream.
- Search internet radio stations by name, browse by country, genre, or category, and filter out stations reported as broken.
- Save favorite stations locally, revisit recently played stations, and use a dedicated internet-radio player with previous/next station switching.
- Continue playback in the background with Android media controls, lock-screen metadata, audio-focus handling, and safe headphone-disconnect behavior.
- See whether Radio Browser considered a station online and when it last checked the stream; this does not claim the programme itself is live.
- Follow the phone's light or dark appearance throughout the app.
- Search for podcasts through Apple's public catalog using English, mainland-Chinese, or Hong Kong storefront scopes; Apple supplies discovery metadata, not episode audio.
- Add a publisher RSS/Atom feed directly, browse its episodes, subscribe locally, stream from publisher enclosure URLs, and resume from the saved listening position.
- Open a dedicated podcast player, build an episode queue, jump between or remove queued episodes, choose 0.75×–2× playback speed, and set a service-owned 15–60 minute sleep timer.
- Refresh subscribed feed metadata automatically about every 12 hours through Android WorkManager when the phone is online and its battery is not low.
- Use a searchable Unplayed inbox across subscriptions, search within a show's episodes, and mark episodes played or unplayed without downloading audio.

The app intentionally has no YouTube downloader, stream extractor, hidden player, podcast downloader, or offline audio cache.

## Podcasts

Apple's Search API is used only to locate a show's public feed and official catalog page. OpenGroove does not play or cache Apple preview media and does not reuse Apple search-result artwork. After a show is opened, its publisher-controlled RSS or Atom feed supplies the description, artwork, episodes, and public audio enclosure URLs.

The language controls choose a discovery storefront: US for English/all, mainland China for Chinese, and Hong Kong for Cantonese. They improve regional discovery but do not assert that every returned episode uses that language. A publisher feed URL can always be added manually.

Subscribing and saving progress are local database operations. They do not download an episode. Podcast streams remain under the publisher's control and may change, require authentication, contain advertising, or become unavailable. Users should follow each publisher's terms.

Automatic refresh is deliberately inexact and metadata-only. Android schedules one unique periodic job, and power-saving modes may defer it. A failed publisher feed is skipped until the next cycle so other subscriptions still refresh. No episode enclosure is opened by the background worker.

## Internet radio

Radio discovery uses the community-run [Radio Browser](https://www.radio-browser.info/) directory. Station entries include their resolved public stream URL, country, language, tags, codec, and bitrate. OpenGroove reports station plays through Radio Browser's click endpoint and never records or downloads a stream.

Some legacy stations still publish HTTP-only streams, so Android cleartext playback is enabled for radio compatibility. OpenGroove sends its own directory and catalog requests over HTTPS and does not transmit account credentials.

Radio Browser indexes public station URLs but does not grant rights to rebroadcast or record them. OpenGroove acts as a tuner: streams remain under each station's control and can disappear or be geographically restricted.

"Online when checked" means Radio Browser recently reached the stream. "Streaming" means the current phone player is receiving it. Neither label proves that a presenter or event is live; verified programme metadata will require a broadcaster-backed schedule provider.

## Privacy

OpenGroove has no account, advertising SDK, or analytics. Playlists, saved stations, recent stations, podcast subscriptions, episode metadata, and playback progress are kept on the device. Search terms and normal network metadata are sent only to the selected catalog/directory or to an official external platform opened by the user. See [PRIVACY.md](PRIVACY.md).

## Build

Requirements: JDK 17 and Android SDK 36.

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
