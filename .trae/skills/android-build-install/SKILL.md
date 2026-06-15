---
name: "android-build-install"
description: "Build and install Android debug APK to a connected device on this Windows workstation. Invoke when user asks to build an Android project, run assembleDebug, install APK, debug Android, or troubleshoot Android build errors. Encapsulates the local Gradle/JDK/SDK paths and the proven fix patterns."
---

# Android Build & Install (Windows Workstation)

End-to-end playbook for building an Android project debug APK and installing it onto a connected device, using the local toolchain on this Windows machine.

## When to invoke this skill

- User says: "构建 / 编译 / 打包 Android 项目", "生成 debug 包", "install 到手机", "跑一下 Android 工程"
- An assembleDebug task has failed and the cause is unclear
- A user says "用本地环境" / "本机有 Android 环境"

## Local environment (verified)

| Tool | Path | Notes |
|---|---|---|
| Android Studio JBR | `C:\Program Files\Android\Android Studio\jbr` | **Use as JAVA_HOME** - has jlink.exe |
| Android SDK | `C:\Users\Squema-Mini\AppData\Local\Android\Sdk` | ANDROID_HOME and ANDROID_SDK_ROOT |
| adb | `C:\Users\Squema-Mini\AppData\Local\Android\Sdk\platform-tools\adb.exe` | No PATH - use full path |
| Gradle 8.5 | `C:\Users\Squema-Mini\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5\bin\gradle.bat` | Pre-downloaded |

## Verified build command

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\Squema-Mini\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = "C:\Users\Squema-Mini\AppData\Local\Android\Sdk"
& "C:\Users\Squema-Mini\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5\bin\gradle.bat" assembleDebug --no-daemon
```

## Verified install command

```powershell
$apk = "<projectRoot>\app\build\outputs\apk\debug\app-debug.apk"
& "C:\Users\Squema-Mini\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices
& "C:\Users\Squema-Mini\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s <deviceId> install -r -t $apk
```

## Recurring failure modes and verified fixes

### 1. `error: style attribute 'attr/colorXxx' not found`
- **Cause**: Material attribute without android: prefix
- **Fix**: Use android: prefix, e.g. android:colorBackground

### 2. `error: attribute android:cornerRadius not found`
- **Cause**: Requires API 31+, but minSdk is lower
- **Fix**: Remove or use shape drawable instead

### 3. `[kapt] tools.jar not found`
- **Cause**: KAPT incompatible with Java 21
- **Fix**: Migrate from KAPT to KSP

### 4. `jlink executable does not exist`
- **Fix**: Set JAVA_HOME to JBR path

### 5. `Unresolved reference: R`
- **Fix**: Add import com.<appId>.R

### 6. `AccessDeniedException: kotlin-daemon-client-*.tmp`
- **Fix**: Use --no-daemon flag

### 7. `unresolved reference: getAlignment`
- **Cause**: Extension function from another package not imported
- **Fix**: Add import or inline the logic

### 8. `variable expected` on `?.let { } ?:`
- **Cause**: lines += is not an expression
- **Fix**: Use if-else instead

### 9. `unresolved reference: next????` (string interpolation)
- **Cause**: `$next请` is parsed as variable `next请`
- **Fix**: Use `${next}请` with braces

## Standard workflow

1. Inspect project: LS/Read build.gradle.kts
2. Check Gradle version at gradle/wrapper/gradle-wrapper.properties
3. Run build command
4. Fix errors using failure mode catalog
5. Locate APK at app/build/outputs/apk/debug/
6. Install with adb
