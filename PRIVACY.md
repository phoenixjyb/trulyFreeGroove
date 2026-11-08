# OpenGroove privacy notice

OpenGroove is a personal music discovery, internet-radio, and podcast app.

## Data kept on the device

The app stores playlists, saved and recently played radio stations, podcast subscriptions, cached episode metadata, listening progress and played/unplayed state, saved YouTube video references, and player metadata in a local Room database on the Android device. Existing playlist and radio preferences are copied into that database once. Cached YouTube titles, channel names, thumbnails, availability, and related API metadata are refreshed or removed within 30 days. OpenGroove does not operate an account or synchronization service and does not send this library to the developer.

## Optional Home Music

Home Music connects Android directly to a Navidrome server you configure on your
home network. The server URL, username and password are encrypted with an
Android Keystore key and stored in the app's backup-excluded private directory.
Disconnect deletes that saved configuration. Queue/playlist entries retain only
server/account identity and opaque song references, with titles and artist/album
metadata; authenticated stream URLs and passwords are never stored there.

Your server receives album requests, search terms, token authentication and audio
requests, and may retain its own logs/listening records under its configuration.
OpenGroove does not send this information to the developer. HTTP on home Wi-Fi
is unencrypted, including metadata, token authentication and audio; use a dedicated
music password on a trusted LAN, or trusted HTTPS. Token authentication avoids
sending the raw password but does not make HTTP confidential. No collection is
uploaded, no audio is cached offline, and no public sharing endpoint is created.
Home Music metadata is requested only when you use that feature. Audio continues
through the existing foreground playback service after you leave the screen.

## Network requests

OpenGroove contacts Wikimedia Commons, an optionally configured Jamendo account, Radio Browser, and Apple's public podcast Search API to perform searches and obtain provider-controlled media or directory metadata. When configured by the person building the app, Android YouTube searches contact YouTube Data API v3 and send the search terms plus the app package and signing-certificate identity required for an Android-restricted API key. When a podcast is opened or refreshed, OpenGroove contacts its publisher RSS/Atom host. Subscribed feeds are also refreshed automatically about every 12 hours through Android WorkManager while network and battery constraints permit. Those services receive the normal information needed for an internet request, such as the device IP address, request time, app user-agent, and applicable search terms. Their own privacy policies apply.

Opening the in-app YouTube Watch screen loads YouTube's official embedded player. YouTube receives the app identity/referrer and the normal data required to render the player, enforce availability and account restrictions, prevent abuse, and serve any applicable advertising. The player does not autoplay; loading its thumbnail and controls may still contact YouTube before the user presses Play. YouTube's privacy policy and terms apply.

If YouTube requires sign-in or bot verification, the user can choose the signed-in browser fallback. It opens the official YouTube watch page in an Android Custom Tab, which uses the selected browser's existing cookies, account state, privacy settings, and history behavior. OpenGroove cannot read or copy those browser cookies.

When a user opens YouTube Music, YouTube, Spotify, QQ Music, NetEase Cloud Music, a station or podcast website, a source page, or a license page, that destination handles the visit under its own privacy policy.

## Playback

Audio is streamed from the provider, broadcaster, or podcast publisher that supplied the media URL. OpenGroove does not proxy, record, redistribute, upload, or download radio and podcast streams. Automatic podcast refresh reads feed metadata only and does not request episode audio enclosures. Android system media controls may display the current title, station or episode, and publisher artwork while playback is active.

YouTube playback is audiovisual and remains inside YouTube's visible official player. OpenGroove does not extract a YouTube audio or video URL, cache media, remove player controls or advertising, or continue YouTube playback while the player screen or app is hidden.

## Analytics and advertising

The app includes no advertising SDK and no developer-operated analytics or crash-reporting service.

This notice must be reviewed if accounts, cloud synchronization, analytics, crash reporting, or offline media downloads are introduced.
