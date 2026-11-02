# Build the Swipe Gallery APK

## Requirements

- JDK 17, Gradle 8.9, Android SDK Platform 35, and Android Build Tools. These have been installed under `C:\Program Files\Eclipse Adoptium` and this project's `tools` directory on the current machine.
- A local release key is stored under the ignored `tools` directory. It signs release APKs for device installation; keep it to sign future updates with the same certificate.
- The checked-in Gradle wrapper downloads Gradle 8.9 from the official Gradle distribution server on machines with direct Java network access.

The Android Gradle Plugin and Kotlin plugin versions are pinned in `build.gradle.kts`. The first build downloads the required Gradle and Android dependencies.

## Windows PowerShell

Run these commands from the project root (`E:\swipe-gallary`). Configure the installed JDK, SDK, and local Gradle distribution in this PowerShell session:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = (Join-Path (Get-Location) 'tools\android-sdk')
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = (Join-Path (Get-Location) 'tools\gradle-home')
```

Build an installable debug APK:

```powershell
.\tools\gradle-8.9\bin\gradle.bat --no-daemon clean assembleDebug
```

The APK is written to:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Build an optimized, signed release APK:

```powershell
.\tools\gradle-8.9\bin\gradle.bat --no-daemon clean assembleRelease
```

Release output:

```text
app\build\outputs\apk\release\app-release.apk
```

Install it on a connected device:

```powershell
.\tools\android-sdk\platform-tools\adb.exe install -r .\app\build\outputs\apk\release\app-release.apk
```

Verify the APK signature:

```powershell
.\tools\android-sdk\build-tools\35.0.0\apksigner.bat verify --verbose .\app\build\outputs\apk\release\app-release.apk
```

Install the debug APK on a connected device with Android Platform Tools installed:

```powershell
.\tools\android-sdk\platform-tools\adb.exe install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## macOS / Linux

```bash
gradle wrapper --gradle-version 8.9
chmod +x ./gradlew
./gradlew --no-daemon clean assembleDebug
./gradlew --no-daemon clean assembleRelease
adb install -r ./app/build/outputs/apk/debug/app-debug.apk
```

## Smaller release APK

The release variant enables R8 code shrinking and resource shrinking. The app has no bundled image assets and uses Android's system image store. For Play Store distribution, prefer an Android App Bundle so each device gets only the resources and native libraries it needs:

```powershell
.\tools\gradle-8.9\bin\gradle.bat --no-daemon clean bundleRelease
```

To inspect APK size and contents from Android SDK Build Tools:

```powershell
.\tools\android-sdk\cmdline-tools\latest\bin\apkanalyzer.bat apk file-size .\app\build\outputs\apk\release\app-release.apk
.\tools\android-sdk\cmdline-tools\latest\bin\apkanalyzer.bat apk analyze .\app\build\outputs\apk\release\app-release.apk
```

Bundle output: `app\build\outputs\bundle\release\app-release.aab`. Configure a separate signing key before distributing through Google Play.
