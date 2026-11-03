# Simple Notes

<img alt="Logo" src="graphics/icon.png" width="120" />

Fully open source notes app with no ads, tracking or unnecessary permissions. Tons of cool features and customizable colors, encrypted exports and no internet access required, made with privacy and simplicity in mind.

Simple-Notes (GPL-3.0) was my favorite FOSS notes app until the project was sold to a shady company named ZipoApps in 2023. I forked it to keep it alive, FOSS and updated. This fork is not affiliated with Simple Mobile Tools, Fossify, or ZipoApps. Code contributions are very welcome :)

<div style="display:flex; gap:8px; flex-wrap:wrap;">
<img alt="Notes" src="graphics/screenshots/screenshot-notes.png" width="22%">
<img alt="Notebooks" src="graphics/screenshots/screenshot-notebooks.png" width="22%">
<img alt="Inside notebook" src="graphics/screenshots/screenshot-inside-notebook.png" width="22%">
<img alt="Customize Widgets HomeScreen" src="graphics/screenshots/screenshot-home-widgets.png" width="22%">
</div>

APKs are published on [GitHub Releases](https://github.com/t0ma5/tomato-notes/releases)
- `Tomato-Notes_<version>-FOSS-arm64-v8a.apk` (most phones)
- `Tomato-Notes_<version>-FOSS-armeabi-v7a.apk`
- `Tomato-Notes_<version>-FOSS-x86_64.apk` (emulators)
- `Tomato-Notes_<version>-FOSS-universal.apk` (all included)

## New features in this fork

The Simple Notes Pro workflow, plus notebooks, locks, and a recycle bin. Still offline, still GPL-3.0.

**Organize**
- **Notebooks** — Create, rename, pin, reorder, lock, and delete collections of notes. A toolbar icon switches a two-column notes grid and the notebooks screen (notes is the default).
- **Search and tags** — Find notes, notebooks, and tags from one screen. Move a note to another notebook, or send selected checklist items to a different note.
- **Recycle bin** — Restore deleted notes and notebooks.

**Private**
- **Lock** a note or a whole notebook with fingerprint, pattern, PIN, or password. Locked content stays hidden in previews, search, widgets, share targets, and the recents screen until you unlock.
- **Read-only** — Freeze a text note, checklist, or counter so it cannot be edited by accident.
- **Encrypted exports** — AES-256-GCM with a password (PBKDF2-SHA256 key), or plain text with none. Automatic backups run in the background and restart after a reboot.

**Write**
- Text notes with Markdown preview, checklists, and counter notes with colored +/− buttons.
- Undo and redo. Paste as plain text; composing keyboards still work.
- Checklists: uncheck all, collapse checked items, sort each list on its own.
- **Checklist export** — Settings export writes checklist items in the order you see, including custom order and done items at the bottom. Import keeps that order.
- **Print** — If the print page fails to load, a toast reports the error.
- **Checklist dialogs** — Add and rename keep the text you typed when the phone rotates.

**Home screen**
- Notes widgets and a notebook widget. Customize widget colors once; every widget updates. A widget shows when its note is deleted and comes back when you restore it.

**No nags**
- No donation, rate, or “what’s new” popups. Own app ID (`tomato.notes`) so it can sit next to Simple Notes Pro.

## Build

Release APKs come from `assembleCoreRelease` (local and GitHub Actions). Names:

- `Tomato-Notes_<version>-FOSS-arm64-v8a.apk`
- `Tomato-Notes_<version>-FOSS-armeabi-v7a.apk`
- `Tomato-Notes_<version>-FOSS-x86_64.apk`
- `Tomato-Notes_<version>-FOSS-universal.apk`

| Item | Value |
| --- | --- |
| App ID | `tomato.notes` |
| GitHub repo | [t0ma5/tomato-notes](https://github.com/t0ma5/tomato-notes) |
| App version | 1.1 (versionCode 101) |
| minSdk | 23 |
| targetSdk / compileSdk | 36 (Android 16) |
| JVM bytecode | 17 |
| Gradle | 9.1.0 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / KSP | 2.2.10 |
| Room | 2.8.4 |
| Material Components | 1.12.0 (via Commons, bumped by `scripts/patch_commons.py`) |
| Local JDK | 25 (do not point Gradle at JDK 17) |
| CI JDK | Temurin 17 |
| Commons | `SimpleMobileTools/Simple-Commons` @ `eceb48949e`, patched by `python scripts/patch_commons.py` |

Checkout Simple-Commons next to the app (gitignored `Simple-Commons/`), run `python scripts/patch_commons.py`, then `./gradlew assembleCoreRelease`. When that directory exists, Gradle `includeBuild`s it instead of the JitPack AAR. Sign with gitignored `keystore.properties` and `app/keystore.jks` (GitHub secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
