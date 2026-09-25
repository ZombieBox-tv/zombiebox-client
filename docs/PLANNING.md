# zombiebox-client: component work

## dev.54 TV controls, diagnostics and IPTV setup

The YouTube player starts with its recommendation rail hidden. D-pad Down
opens the rail and details, Up returns to the controls, and a focusable timeline
supports short left/right seeks; Back still hides the chrome. Preserve the
optimistic play state while a manual quality change buffers. Replace legacy
diagnostics dialogs with dark D-pad TV pages. Show a localized M3U setup action
when IPTV has no configured playlist. JVM tests, lint and source build pass;
the selected Vizio still needs visual/focus and media QA. APK versionCode 54;
no product milestone closes.

## dev.53 probe clock and related retry

Use gateway time for newly measured playback probes on devices with incorrect
system clocks, refresh authenticated Client version before rerunning the suite,
and explain Auto-only quality choices. Retry a related YouTube page only for
bounded transient worker failures; never retry an authorized empty result or
an authentication error. JVM, release build and Vizio physical evidence are
recorded separately. On the selected Vizio/API13, the completed suite saved
14 probe results with gateway time despite the device's 2010 clock. A YouTube
video then offered Auto, 360p, 240p and 144p rather than Auto alone, and showed
topic-related items below the player. Spotify sound, AirPlay media, receiver
recovery and broad API compatibility remain open.

## dev.52 player controls and receiver continuity

Use a transient fullscreen TV chrome with immediate play/pause feedback, D-pad
quality choices, current-video details and paged related YouTube items. Preserve
queue, incoming-receiver ownership, position and paused state when switching
quality. Keep explicit YouTube and media-receiver intent across lease loss and
service configuration while never replacing another device's claim. JVM tests,
lint and release assembly pass. A locally signed dev.52 update installed over
dev.51 on the selected Vizio/API13 with the same certificate and retained data;
Home and the YouTube feed rendered and responded to D-pad navigation. The
fullscreen quality dialog opened from its D-pad gear, but this played source
offered Auto only; manual switching, related-item relevance, Spotify
audio/artwork, AirPlay media and all receiver-recovery paths still need
physical interaction. No product milestone closes.

## dev.51 provider navigation and receiver account states

Use full-screen, dark D-pad search for catalog providers and guard unsupported
catalog routes, including AirPlay. Keep empty-state actions tied to service
readiness. The Spotify tab reads the gateway integration state and authorization
mode, distinguishes an unpaired account from an unavailable worker, and offers
receiver selection plus the selected Zeroconf or device-code guidance. The
AirPlay tab exposes receiver selection beside its PIN and audio controls.
Browser keyboard Back/Up returns to an enabled control. Playback requests show
bounded start, delay and failure feedback; this does not resolve the observed
Vizio YouTube playback failure. APK versionCode 51 is a local source/build
checkpoint. Device behavior, iOS AirPlay media, Spotify discovery/account
pairing, browser focus, YouTube playback and full TV UX acceptance still need
physical retesting. No product milestone closes.

## dev.50 TV shell and first Vizio follow-up

Bundle Barlow and crisp service marks, give service cards distinct accent
treatment, and expose the transient four-digit AirPlay PIN in its app section
through the paired gateway API. The PIN is separate from the six-digit operator
code. Replace legacy search, receiver and browser controls with dark,
D-pad-readable TV views; put a real YouTube video grid and per-service search in
the provider tabs, and keep an idle player out of the bottom navigation path.
The selected API-13 Vizio displayed a real YouTube feed/catalog and the AirPlay
PIN from a locally signed QA APK. Those checks do not establish video playback:
the selected YouTube item still failed, and iPad AirPlay authentication/media,
Cast pairing, browser interaction, and broader focus behavior need physical
retests. The public Full dev.52 image does not include the new PIN route. No
product milestone closes.

## dev.45 IPTV category navigation

Select current M3U categories from the IPTV catalog, combine with favorites and
search, and preserve the semantic filter through Back/recreation. EN/ES controls
accompany APK versionCode 45. Physical D-pad and playlist acceptance remain.

## dev.44 IPTV favorites

Add a focused favorite action and paged/searchable IPTV favorite location with
Back and semantic saved-state restoration. English/Spanish resources and JVM
navigation evidence accompany versionCode 44; physical focus/player tests remain.

The product milestones relevant to this repository are M1, M2, M3, M4, M5, M6, M7, M8, M9, M11.
The local registry is a component projection of the workspace plan. Closing a
component task does not close a product-wide milestone or a physical validation gate.

Current increment: independent repository/build/dependency boundaries with filtered
history. Remaining feature development follows the ordered workspace audit:
tracks/subtitles and lifecycle; provider navigation/virtualization; measured
capabilities/native-first health; remote media adaptation; receiver finishing;
Edge operations and reproducible releases. Implement only this component's part,
and evolve shared protocol contracts in their owning repository.

Keep a separate validation track for hardware/account/latency/memory evidence.
Use development checkpoint tags until complete exit gates are evidenced. Hosted
issues/milestones can be attached to the shared GitHub Project once remotes exist.

## dev.11 increment

Recycled provider list, scoped search, bounded history and stable focus/scroll snapshots. Home virtualization and full rich-screen navigation remain open.
No product milestone or physical/account gate is completed by this checkpoint.

## dev.12 increment

Foreground media-receiver selection, metadata updates, audio panel, interrupted-playback restoration and bounded reconnect. Rich artwork, complete screen stack and background ownership remain open.

## dev.13 increment

Foreground/surface intent, guarded media callbacks, finite compatible retry and fresh external fallback; client versionCode 13.
No product milestone or physical/account gate closes with this checkpoint.

## dev.14 increment

Service-owned playback across Activity recreation/background, paged next-content queue, notification controls and retained semantic subtitle selection; client versionCode 14.
The four requested block-1 changes are implemented; physical acceptance and broader product gates remain open.

## dev.16 increment

Seven-card Home windows, bounded artwork cache, IPTV time guide, extended diagnostics, native audio-focus health, browser pointer input and service-owned receiver restoration. Client versionCode 16.
Product exit gates and physical/account acceptance remain open.

## dev.17 increment

Restores Home/catalog/browser navigation, adds memory-tier images, local multicast diagnostics and integration hints. APK code 17 includes GPL notices.

No product milestone or physical gate is closed.

Artwork encoded caching now survives Home rerenders, expires after five minutes
and clears on connection changes. Physical scroll/bitmap-memory evidence remains open.

## dev.21 increment

Measured network preference, debounced federated search, and evidence-gated API14 TextureView with baseline fallback and explicit diagnostics. VersionCode 21.
No physical, account or product milestone closes.

Verification: 65 JVM tests, debug/release compilation, lint and APK audit pass. The debug APK is 1,248,788 bytes, minSdk 9, single DEX 035 and contains no native libraries. Physical output/probe behavior remains unverified.

## dev.22 increment

Explicit receiver switching, Cast handoff preference, semantic YouTube lease expiry and service-owned interrupted-session restoration. APK versionCode 22.
No product or physical acceptance gate closes.

Verification: 69 JVM tests, debug/release builds, lint and APK compatibility audit pass. Interrupted receiver chains and cancelled pending YouTube resolution are covered; physical A/V, foreground transitions and OEM behavior remain unverified.

## dev.23 increment

Manual URL plus automatic gateway candidates and guarded modern system-bar insets; APK versionCode 23.
Product exit gates and deferred physical acceptance remain open.

Verification: 71 JVM tests, debug/unsigned-release builds, lint and APK audit pass. Debug APK: 1,258,607 bytes, minSdk9, single DEX035 and no native libraries. Physical/visual behavior remains unverified.


## dev.24 increment

Gateway-generated QR display, local six-digit consent/revocation and guarded discrete remote input for Home/player/catalog. API9 thin-client/no-native boundary remains; APK versionCode 24.
Full visual/capture policy, extended Remote, HEVC/4K and other product gates remain open; physical acceptance stays deferred.

Verification: 74 JVM tests, debug/unsigned-release builds, lint and APK audit pass. Debug APK: 1,282,973 bytes, minSdk9, single DEX035 and no `.so`. Long-poll companion events wake bounded input consumption without refreshing Home; periodic polling remains a heartbeat/fallback. Physical camera/input/verifier behavior is unverified.

## dev.25 increment

Isolated API21 decoder candidate discovery and prerequisite-gated extended diagnostics with measurement timestamps. No native libraries; display/encoder/Advanced and physical gates remain open.

## dev.27 increment

Consumes the additive Cast request schema pin. Shared Android transport and Client implementation are unchanged; the thin APK retains the dev.25 version and no-native-library requirement.

## dev.29 increment

Updates the additive protocol pin only. Client source, APK version and no-native contract remain unchanged. Actual audio-only Cast playback remains a deferred physical gate.
Product milestones and physical acceptance remain open.

## dev.30 increment

Incoming file plans preserve VOD mode/seekability; completion stops the file and restores interrupted playback instead of live reconnection. No native library added.
Product milestones and deferred physical gates remain open.

## dev.31 increment

Isolated API16/21 codec-role/profile inventory, API17 logical displays, API23 output modes and API29 acceleration declarations; bounded EN/ES native inventory UI. Inventory remains separate from functional playback evidence.
Product milestone and physical/public distribution gates remain open.

## dev.32 increment

Adds optional API21 native MediaSession/MediaStyle controls, semantic action availability, VOD timeline seek and operation-error fallback with persistent cooldown. Advanced can disable integration. No DIAL/CEC or Bluetooth/HDMI physical qualification is claimed.
Product milestones, physical validation and public distribution remain open.

## dev.33 increment

Optional privileged AOSP HDMI display query/one-touch play with timeout, cooldown and local Advanced policy; quiet Home header icons and a responsive docked status sidebar. VersionCode 33. Native DIAL, broad vendor bindings and physical acceptance remain open.

## dev.34 implementation checkpoint

Network pairing consent/24-hour ignore, five-minute QR display, focus-scoped remote text in owned fields, guarded Whisperplay Home launch, lifecycle clock and Hero refresh/metadata improvements. APK versionCode 34; runtime/OEM acceptance remains open.
Product exit gates and deferred physical acceptance remain open.

## dev.35 checkpoint

Periodic opt-in playback adaptation, completion-aware incoming file queues and explicit universal foreground receiver mode. Native DIAL registration and physical qualification remain open.
Product milestone completion still requires its recorded acceptance gates.


## dev.37 implementation checkpoint

Memory-tier Home realization (3/5/7 cards), focus-preserving window policy and versionCode37. Physical acceptance and wider navigation remain open.

## dev.38 implementation checkpoint

Dev.38: service-owned YouTube listening/commands, typed foreground notification and bounded expiry/late-command policy; physical background/OEM acceptance remains open.

## dev.39 navigation increment

Dev.39: bounded search return bookmarks, one-minute result reuse, explicit provider-to-search Back and profile/configuration invalidation. Wider navigation, other V1 packages and physical acceptance remain open.

## dev.40 reception and diagnostics increment

Service-owned idle Spotify/AirPlay/Cast reception, fenced handoff/restoration and bounded live recovery. VersionCode40; no native libraries. Physical background/OEM acceptance remains open. Product milestones remain open.

## dev.41 guide and audio selection increment

Paged IPTV guide with scoped history/focus, failure retry, late-load fencing and owned Cast Remote navigation. VersionCode41. See docs/guide-navigation.md. Product and physical gates remain open.

## dev.42 navigation and preferred-audio increment

Dev.42: one-shot playback return to paged guide, catalog details or search; bounded semantic state survives Activity recreation and failed catalog reloads. Explicit Home/provider navigation clears stale return intent. VersionCode42; physical navigation acceptance remains open.

## dev.43 navigation and functional media increment

Dev.43: retained settings parent menus, bounded recreation bookmarks, stale-load fencing and stable provider identity; Browser D-pad escape, semantic focus restoration and stale-frame cleanup. VersionCode43. Physical navigation acceptance remains deferred.
## dev.46 YouTube account navigation

Client shows the gateway-owned TV OAuth verification code/URL, manual interval-
gated status checks, subscriptions/playlists and device-scoped channel/playlist
browse navigation; disconnect requires the operator code. APK versionCode 46,
minSdk 9 and no native library. A real Google account and device focus remain
unverified. TV Code is independent; no product milestone closes.
