# Cadence

A Rust-first Android music player with a MIUI/HyperOS-inspired interface.

## Stack

- Rust for playback, scanning, metadata and application state.
- Slint for the native Android UI.
- Rodio + Symphonia for local audio playback.
- Lofty for audio metadata.
- Walkdir for recursive library scanning.

Slint officially supports Rust Android applications. The current Android workflow can use cargo-apk or xbuild.

## Build

```bash
rustup target add aarch64-linux-android
cargo install cargo-apk
cargo apk build --target aarch64-linux-android --lib --release
```

Run on a connected device:

```bash
cargo apk run --target aarch64-linux-android --lib
```

Place local music in `/storage/emulated/0/Music`.

## UI

Cadence uses dark graphite surfaces, large rounded cards, compact pills, large typography and a warm orange accent inspired by MIUI/HyperOS patterns without copying Xiaomi assets.

## Roadmap

- MediaSession and lock-screen controls
- Foreground playback service
- Notification controls
- Album-art extraction and caching
- Queue and playlists
- Favorites
- Lyrics
- Search
- Swipe gestures
- Gapless playback
