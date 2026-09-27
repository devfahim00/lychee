# 🍒 Lychee

**A glassy, yt-dlp powered downloader for Android.**

Lychee wraps the full [yt-dlp](https://github.com/yt-dlp/yt-dlp) engine in a frosted-glass Material You style UI. It ships with its own Python 3.14 runtime, ffmpeg + ffprobe, aria2c, QuickJS and curl_cffi — everything yt-dlp needs, nothing else required.

![CI](https://github.com/devfahim00/lychee/actions/workflows/android.yml/badge.svg)

## Features

- 📥 **Video & audio downloads** from thousands of sites via yt-dlp
- 🎨 **Glassmorphism UI** — animated gradient blobs, frosted cards, blur-friendly design
- 🎵 **Audio extraction** — MP3 / M4A / Opus / FLAC with embedded metadata & thumbnails
- 📊 **Format picker** — pick any resolution/fps/container, or audio-only
- 🔐 **Impersonation** — browser TLS fingerprint spoofing via curl_cffi (`--impersonate`)
- ⚡ **yt-dlp updater** — Stable / Nightly / Master channels, update in-app
- 🧩 **Full YouTube support** — yt-dlp-ejs runs on the bundled QuickJS runtime
- 🚀 **aria2c** multi-connection downloads & concurrent fragments
- 💬 **Subtitles, SponsorBlock, custom templates, proxy, extra args**

## yt-dlp documentation compliance

Lychee follows the yt-dlp project's own recommendations:

| yt-dlp doc section | How Lychee implements it |
|---|---|
| [UPDATE](https://github.com/yt-dlp/yt-dlp#update) | In-app updater with all three release channels (stable/nightly/master), replacing the bundled zipapp. The [nightly](https://github.com/yt-dlp/yt-dlp-nightly-builds) channel is selectable and recommended for regular users. |
| [STRONGLY RECOMMENDED](https://github.com/yt-dlp/yt-dlp#strongly-recommended) | **ffmpeg & ffprobe** are bundled in the APK. **yt-dlp-ejs** (bundled in the yt-dlp release binary) runs on the bundled **QuickJS** runtime, registered via `--js-runtimes quickjs:<path>`. |
| [IMPERSONATION](https://github.com/yt-dlp/yt-dlp#impersonation) | The bundled Python environment includes **curl_cffi**, so `--impersonate` works out of the box. Toggle it and pick a target client (chrome / edge / safari / firefox / pinned versions) in Settings. |

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- yt-dlp (zipapp) executed by a Termux-built Python 3.14 runtime
- Runtime packages from [YTDLnis-packages](https://github.com/deniscerri/ytdlnis-packages) & [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (Maven Central)
- FFmpeg, aria2c, QuickJS, curl_cffi, mutagen, brotli, pycryptodomex, websockets

## Build

```bash
git clone https://github.com/devfahim00/lychee
cd lychee
./gradlew assembleDebug
```

The debug APK is built for **arm64-v8a**. Pull the artifact from GitHub Actions or `app/build/outputs/apk/debug/`.

## Credits & licenses

This project stands on the shoulders of giants — thank you:

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) — Unlicense
- [YTDLnis](https://github.com/deniscerri/ytdlnis) — GPL-3.0 (Python runtime package)
- [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (ffmpeg & aria2c packages)
- [Seal](https://github.com/JunkFood02/Seal) — GPL-3.0 (design inspiration)
- FFmpeg — LGPL/GPL · aria2c — GPL-2.0 · QuickJS — MIT · curl_cffi — MIT · yt-dlp-ejs — Unlicense

Lychee is licensed under **GPL-3.0**.

## Disclaimer

Downloading content may violate the terms of service of some platforms. Use Lychee responsibly and only for content you have the right to download.
