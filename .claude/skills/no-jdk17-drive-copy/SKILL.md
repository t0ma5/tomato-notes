---
name: no-jdk17-drive-copy
description: >-
  Never download JDK 17 into %TEMP%, never Copy-Item a failed extract, and
  never copy D:\ into Temp. This machine already has JDK 25 at
  D:\software\jdk-25.0.4. Use when building Tomato Notes, downloading a JDK,
  Expand-Archive, Temurin, disk full, or AppData\Local\Temp\jdk-17.
---

# No JDK 17, no Temp copies of D:\

This PC: `JAVA_HOME=D:\software\jdk-25.0.4`. Tomato Notes runs Gradle 9.1.0 and AGP 8.13.2, which work on JDK 25. `gradle.properties` says the same: do not point `org.gradle.java.home` at JDK 17.

## What filled the disk (twice)

A failed JDK 17 zip left `$extracted` null. PowerShell then did:

```powershell
Copy-Item -Path "$($extracted.FullName)\*" -Destination $env:TEMP\jdk-17-x64 -Recurse
```

`"$($null.FullName)\*"` becomes `"\*"`. With cwd on `D:\WEBSITES\...`, `"\*"` is **all of D:\**. That cloned the drive into `C:\Users\X\AppData\Local\Temp\jdk-17-x64`.

## Do not

- Download Eclipse Temurin / Adoptium / JDK 17 (any zip to `%TEMP%`).
- `Copy-Item` / `Expand-Archive` into `%TEMP%\jdk-17*` or similar.
- Copy from `"\*"`, `"D:\*"`, or `$extracted.FullName` unless `$extracted` is a **real** directory you just listed (`Test-Path` the JDK `bin\java.exe` first).
- `Remove-Item -Recurse` on that Temp tree if it might contain junctions. Use `cmd /c rmdir /s /q` so D:\ is not followed.

## Do

- Build with the JDK 25 already installed. Keep `sourceCompatibility` / `jvmTarget` at 17. CI uses Temurin 17 on GitHub; that is fine.
- KSP on Windows: Gradle cache on C: plus project on D: fails with "this and base files have different roots". Set `GRADLE_USER_HOME=D:\software\gradle-home`. Do not Copy-Item `C:\Users\X\.gradle` onto D:\.
- If Gradle/AGP ever rejects Java 25, bump Gradle and AGP. Do **not** install another JDK.
- Disk full: delete **only** `C:\Users\X\AppData\Local\Temp\jdk-17-x64` (and `temurin-17*` next to it). Never delete `D:\DOWNLOADS` or other real D:\ folders.

## Cleanup (copy only)

```bat
cmd /c rmdir /s /q "C:\Users\X\AppData\Local\Temp\jdk-17-x64"
```
