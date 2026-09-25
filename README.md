# zombiebox-client

Legacy-first Android TV/handheld client; one stable client APK.

This is an independent repository in the Zombie Box workspace.
[Source and milestones](https://github.com/ZombieBox-tv/zombiebox-client) are hosted on GitHub.
Development checkpoints are not stable releases or physical compatibility claims.

Application ID: `io.github.diegog0477.zombiebox.client`; experimental minSdk9.

```sh
make deps-check  # ../zombiebox-protocol or ZOMBIE_PROTOCOL_DIR
make build test
```

Android SDK35/build-tools35.0.0 and JDK21 are the current candidate toolchain.
Android Studio is optional. The independent Gradle build includes `:app` and the
shared library from the pinned protocol repository; it does not include Cast.
`make deps` can restore `.deps/zombiebox-protocol` after a remote is configured.

Features use `domain/model`, `domain/repository`, `data`, `presentation/viewmodel`,
`presentation/ui` and isolated `platform` classes where needed. ViewModels consume
semantic values and injected repositories; UI never decodes JSON/calls HTTP.
Views/XML, MediaPlayer/SurfaceView and manual DI preserve the legacy contract.
No modern AndroidX/Compose/coroutines/JNI. Guard modern APIs through factories;
verify Dalvik on physical hardware separately. Home stays green; providers retain
contextual accents. Keep English defaults and Spanish variants in string resources.

Output: `app/build/outputs/apk/debug/app-debug.apk`. Remote embedded text tracks and language preferences are implemented. Richer provider
actions, bitmap subtitles and operational OEM backends remain unfinished.

## Development rules

Run `make format` and `make format-check`. Formatters are pinned and downloaded
on first use. See [AGENTS.md](AGENTS.md), [history provenance](docs/history.md),
[component work](docs/PLANNING.md) and [local milestone registry](docs/milestones.json).
The central workspace owns product-wide ADRs, the original specification, the UI
reference, M0–M11 exit gates and the complete development/validation gap audit.
Physical devices over USB/ADB are the default; automated checks do not establish
legacy runtime or end-to-end account/media compatibility.

Dev.10 adds [playback tracks and refined Home navigation](docs/playback-tracks.md).
Local audio/text-subtitle controls use feature MVVM and the shared gateway transport;
active section selection is independent of remote focus. Physical visual and media
validation remains pending.

Dev.11 adds [provider navigation](docs/provider-navigation.md) with recycled native
rows, scoped search, bounded history and stable focus/scroll snapshots. Deep browse
is available from “View all”; broader Home virtualization and rich layouts remain open.

Dev.12 adds foreground Spotify/AirPlay selection, automatic active-stream reception,
metadata updates, bounded receiver reconnect and interrupted-playback restoration.
The audio panel is an initial semantic Now Playing view; artwork and seamless
cross-Activity music remain open.

Dev.13: Foreground/surface intent, guarded media callbacks, finite compatible retry and fresh external fallback; client versionCode 13.

## dev.14 increment

Service-owned playback across Activity recreation/background, paged next-content queue, notification controls and retained semantic subtitle selection; client versionCode 14.
The four requested block-1 changes are implemented; physical acceptance and broader product gates remain open.

Dev.16: Seven-card Home windows, bounded artwork cache, IPTV time guide, extended diagnostics, native audio-focus health, browser pointer input and service-owned receiver restoration. Client versionCode 16.

## License

First-party code: [GPL-3.0-only](LICENSE). See [NOTICE](NOTICE) for third-party scope.

Dev.19: Language settings, explicit process-death queue resume, guide/search restoration, asynchronous decoded image reuse and diagnostic export. Client versionCode 19.

Dev.20: Service-owned bounded recovery, Advanced recovery policy, automatic Spotify/AirPlay selection and richer semantic details/resume/start-over actions. APK versionCode 20.

Dev.21: Measured network preference, debounced federated search, and evidence-gated API14 TextureView with baseline fallback and explicit diagnostics. VersionCode 21.

## dev.22 increment

Explicit receiver switching, Cast handoff preference, semantic YouTube lease expiry and service-owned interrupted-session restoration. APK versionCode 22.
No product or physical acceptance gate closes.

## dev.23 increment

Automatic gateway candidates below manual pairing URL, lifecycle-safe discovery state and isolated modern system-bar insets.
No product or physical acceptance gate closes.


## dev.24 increment

Gateway-generated QR display, local six-digit consent/revocation and guarded discrete remote input for Home/player/catalog. API9 thin-client/no-native boundary remains; APK versionCode 24.
Full visual/capture policy, extended Remote, HEVC/4K and other product gates remain open; physical acceptance stays deferred.

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

## dev.34 increment

Network pairing consent/24-hour ignore, five-minute QR display, focus-scoped remote text in owned fields, guarded Whisperplay Home launch, lifecycle clock and Hero refresh/metadata improvements. APK versionCode 34; runtime/OEM acceptance remains open.

## dev.35 increment

Periodic opt-in playback adaptation, completion-aware incoming file queues and explicit universal foreground receiver mode. Native DIAL registration and physical qualification remain open.

## dev.36 distribution

Production signing identity is selected for both APKs; see [release signing](docs/release-signing.md). DIAL protocol notices are embedded. VersionCode 36; no runtime feature or physical qualification claim.


## dev.37 increment

Home realizes three, five or seven cards per row according to heap/physical-memory and TV/handheld budgets. All semantic items remain reachable through focus or paging. This allocation bound does not establish a physical-device memory/performance result.

## dev.38 increment

[YouTube background reception](docs/youtube-background.md) now belongs to the playback service. Screen recreation/detachment preserves the lease; notification Stop disables it. JVM/build evidence remains separate from physical API9/13 and modern Android acceptance.

## dev.39 navigation increment

Dev.39: bounded search return bookmarks, one-minute result reuse, explicit provider-to-search Back and profile/configuration invalidation. Wider navigation, other V1 packages and physical acceptance remain open.

## dev.40 reception and diagnostics increment

Service-owned idle Spotify/AirPlay/Cast reception, fenced handoff/restoration and bounded live recovery. VersionCode40; no native libraries. Physical background/OEM acceptance remains open. Product milestones remain open.

[Background media reception](docs/background-reception.md).

## dev.41 guide and audio selection increment

Paged IPTV guide with scoped history/focus, failure retry, late-load fencing and owned Cast Remote navigation. VersionCode41. See docs/guide-navigation.md. Product and physical gates remain open.

Dev.42: one-shot playback return to paged guide, catalog details or search; bounded semantic state survives Activity recreation and failed catalog reloads. Explicit Home/provider navigation clears stale return intent. VersionCode42; physical navigation acceptance remains open.

## dev.43 navigation and functional media increment

Dev.43: retained settings parent menus, bounded recreation bookmarks, stale-load fencing and stable provider identity; Browser D-pad escape, semantic focus restoration and stale-frame cleanup. VersionCode43. Physical navigation acceptance remains deferred.

## dev.55 playback diagnostics and IPTV input

IPTV playlist and EPG URLs are readable while entering them; credentials remain masked. MediaPlayer records numeric error codes and exception types without logging stream URLs. APK versionCode 55. AirPlay and IPTV playback on the Vizio remain unverified for this candidate.
