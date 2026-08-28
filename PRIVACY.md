# OpenGroove privacy notice

OpenGroove is a personal, account-free music discovery, internet-radio, and podcast app.

## Data kept on the device

The app stores playlists, saved and recently played radio stations, podcast subscriptions, cached episode metadata, listening progress and played/unplayed state, saved YouTube video references, and player metadata in a local Room database on the Android device. Existing playlist and radio preferences are copied into that database once. Cached YouTube titles, channel names, thumbnails, availability, and related API metadata are refreshed or removed within 30 days. OpenGroove does not operate an account or synchronization service and does not send this library to the developer.

## Network requests

OpenGroove contacts Wikimedia Commons, an optionally configured Jamendo account, Radio Browser, and Apple's public podcast Search API to perform searches and obtain provider-controlled media or directory metadata. When configured by the person building the app, Android YouTube searches contact YouTube Data API v3 and send the search terms plus the app package and signing-certificate identity required for an Android-restricted API key. When a podcast is opened or refreshed, OpenGroove contacts its publisher RSS/Atom host. Subscribed feeds are also refreshed automatically about every 12 hours through Android WorkManager while network and battery constraints permit. Those services receive the normal information needed for an internet request, such as the device IP address, request time, app user-agent, and applicable search terms. Their own privacy policies apply.

Opening the in-app YouTube Watch screen loads YouTube's official embedded player. YouTube receives the app identity/referrer and the normal data required to render the player, enforce availability and account restrictions, prevent abuse, and serve any applicable advertising. The player does not autoplay; loading its thumbnail and controls may still contact YouTube before the user presses Play. YouTube's privacy policy and terms apply.

When a user opens YouTube Music, YouTube, Spotify, QQ Music, NetEase Cloud Music, a station or podcast website, a source page, or a license page, that destination handles the visit under its own privacy policy.

## Playback

Audio is streamed from the provider, broadcaster, or podcast publisher that supplied the media URL. OpenGroove does not proxy, record, redistribute, upload, or download radio and podcast streams. Automatic podcast refresh reads feed metadata only and does not request episode audio enclosures. Android system media controls may display the current title, station or episode, and publisher artwork while playback is active.

YouTube playback is audiovisual and remains inside YouTube's visible official player. OpenGroove does not extract a YouTube audio or video URL, cache media, remove player controls or advertising, or continue YouTube playback while the player screen or app is hidden.

## Analytics and advertising

The app includes no advertising SDK and no developer-operated analytics or crash-reporting service.

This notice must be reviewed if accounts, cloud synchronization, analytics, crash reporting, or offline media downloads are introduced.
