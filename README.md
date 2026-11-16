# FocusGuard

FocusGuard is a native Android (Kotlin + Jetpack Compose) focus and app-usage limiter.

## Current implementation

- Android 12+ (minSdk 31), Material 3 Compose interface.
- Usage Access (UsageStatsManager) based foreground-app monitoring.
- Per-app daily and continuous-session limits, stored locally with Room.
- Full-screen application-overlay warning (requires the user's explicit overlay permission).
- PIN verifier using PBKDF2-HMAC-SHA256 and an Android Keystore HMAC key; no plaintext PIN is stored.
- Increasing lockout after repeated incorrect PIN attempts.
- Timed focus sessions persisted in DataStore and scheduled with WorkManager.
- Device Owner / Lock Task Mode integration is available as an explicitly provisioned kiosk path.

## Important Android limitations

A regular Android app cannot silently or absolutely lock the whole operating system. App blocking depends on Usage Access, overlay permission, Android background execution policy, and OEM battery management. The monitoring service is user-visible and can be stopped by Android or the user. When monitoring or overlay permissions are missing, FocusGuard reports that blocking is not active rather than claiming success.

True Lock Task kiosk mode requires the device to be provisioned as Device Owner (normally during device setup, or on a dedicated test device). It is not enabled by an ordinary runtime permission. Do not provision your everyday device as a kiosk device without understanding the management and recovery implications.

Emergency Android functions, system security surfaces, Home/Recents behavior, and the notification shade remain under Android's control. FocusGuard is a productivity aid, not a replacement for Android parental controls or a security boundary.

## Build

1. Open this repository in a current Android Studio release.
2. Install Android SDK Platform 36 and JDK 17.
3. Let Android Studio sync Gradle.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).

The project uses Android Gradle Plugin 9.4.0 and Gradle 9.6.0. The Gradle wrapper properties are included; if the wrapper scripts are not present in your checkout, use Android Studio's Gradle integration or generate wrapper scripts with an installed Gradle 9.6.0 distribution.

## First run

1. Open FocusGuard and grant Usage Access from Android Settings.
2. Grant "Display over other apps" so the warning can appear above selected apps.
3. Allow notifications so monitoring status is visible.
4. Create a PIN in Settings.
5. Add apps and set their daily/session limits.
6. Start monitoring from the dashboard.
7. Select distracting apps in the Apps screen and start a timed focus session when needed.

## Privacy

No account, analytics SDK, advertising SDK, or network permission is included. App labels, configured limits, PIN verifier, focus sessions, and usage totals stay in the app's local Room/DataStore storage.

## Validation status

Source and Gradle configuration are committed here. A successful Android build and device-level enforcement test still require an Android SDK/Gradle runner and a physical Android 12+ device; do not treat source upload alone as proof of runtime testing.
