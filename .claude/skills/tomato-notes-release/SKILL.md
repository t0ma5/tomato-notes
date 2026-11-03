---
name: tomato-notes-release
description: >-
  Tomato Notes local APK signing, README new-features list, CHANGELOG,
  fastlane changelog, and GitHub Release notes. Use when building the APK,
  bumping the version, tagging, writing a changelog, or adding a
  user-facing feature.
---

# Tomato Notes release notes and signing

If you build locally, always sign with the same GitHub key. Always add new features to the README and to the GitHub release notes.

## Local builds

- Never build unless the user asked (`ask-before-build`). Local builds are arm64 only (`local-arm64-apk`): `assembleCoreRelease -PlocalArm64=true`.
- Output: `app/build/outputs/apk/core/release/Tomato-Notes_<version>-FOSS-arm64-v8a.apk`. The rename runs in the `assembleCoreRelease` `doLast` in `app/build.gradle.kts`.
- GitHub CI builds all four: `...-arm64-v8a.apk`, `...-armeabi-v7a.apk`, `...-x86_64.apk`, `...-FOSS-universal.apk`.
- Sign with the GitHub keystore, never the debug key. Key files are gitignored: `keystore.properties`, `app/keystore.jks`.
- Notes and Gallery share one keystore (alias `gallery`). Backup: `D:\WEBSITES\PUTTY\simple-gallery-release.jks`, `.properties`, `.jks.b64`. Never generate a new key: installed apps would stop updating.
- GitHub secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`. CI fails if `KEYSTORE_BASE64` is missing.
- Debug builds add `.debug` to the app ID. Never ship a debug APK as a phone update.
- JDK 25 plus `GRADLE_USER_HOME=D:\software\gradle-home`. Do not download JDK 17 (`no-jdk17-drive-copy`).

## Version

`gradle/libs.versions.toml`: `app-version-versionCode` and `app-version-versionName`. The README build table repeats both, so update it too.

## README and GitHub Release

When you add a user-visible feature:

1. Add a bullet under **New features in this fork** in `README.md`, in the right group (Organize, Private, Write, Home screen, No nags).
2. Add the same item to the newest `## New in <version>` section in the `body:` of **Publish GitHub Release** in `.github/workflows/build.yml`.
3. Add it to `CHANGELOG.md` under the current version.
4. Add it to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

Every GitHub Release **must** have a numbered user-facing list in `body:`. Never ship a tag with only auto-generated git notes. `generate_release_notes: true` can stay for the commit log underneath.

### Release text layout (from 1.3 on)

The `body:` starts with `## New in <version>` on its very first line. **Nothing above it**: no intro, no blank line, no note. These lines were removed on purpose and must never come back, in any wording:

- the APK / ABI list (`Tomato-Notes_<version>-FOSS-arm64-v8a.apk`, `armeabi-v7a`, `x86_64`, `universal`, "for most phones", "for emulators", ...)
- the applicationId line (`Release-signed FOSS APKs for applicationId tomato.notes`, "sits next to Simple Notes Pro", "Launcher name is Notes")
- the versionCode line (`New app id tomato.notes (versionCode 100)` or any "versionCode N" mention)
- the `tomato.simple.notes` uninstall / "does not update installs of tomato.simple.notes" warning
- the "Notes below roll up" line (or any sentence explaining that older sections follow)

The APK names live in `README.md`; the GitHub assets list shows the files.

Older sections roll up below the new one, newest first, unchanged:

```
## New in 1.3

1. **Short title** — One or two sentences of what changed and why it matters.
2. **Short title** — ...

## New in 1.2

1. ...

## New in 1.1
...
```

For a new release, insert the new `## New in <version>` section at the top of `body:` and leave every older section below it as it is. Never replace or delete an older section. Group related work under one number. Match the previous release: bold title, em dash, concrete behavior.

This applies to future releases only. Don't edit the text of releases already published on GitHub.

## One build per release

CI (`build.yml`) runs on pushes to `master` and on `v*` tags. To avoid building the same commit twice:

- The release commit message must start with `Release ` (e.g. `Release 1.2: ...`). The master build skips commits whose message starts with `Release `; the tag build is the only one.
- Push the release commit and the tag together: `git push origin master` then `git push origin v<version>`. Don't push master earlier "to check it compiles" and then tag the same commit.
- To check that unreleased work compiles, push it as a normal (non-`Release `) commit; that builds once on master. Then the version-bump commit and tag build once more on the tag.
- Docs-only pushes (README, CHANGELOG, CONTRIBUTING, LICENSE, fastlane, graphics, .claude) don't build at all (`paths-ignore`). Tag pushes always build.
- For any other push that shouldn't build, add `[skip ci]` to the commit message.

## Names

- App ID `tomato.notes`. The old ID `tomato.simple.notes` does not update to it.
- Launcher name **Notes**. About name **Tomato Notes**.
- Releases: tags `v*` on `t0ma5/tomato-notes` trigger **Publish GitHub Release**.
