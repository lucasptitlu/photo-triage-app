# Photo Triage – Android app

Finds groups of similar photos on your phone, pre-selects the sharpest one in each
group, lets you confirm, then moves the rejects to the Android trash.
Requires Android 11 or newer. The app has no internet permission: photos never leave the phone.

## Option A – build on GitHub (nothing to install)
1. Create a free account on github.com, then a new **private** repository.
2. Click "uploading an existing file" and drag in **the contents** of this folder
   (including the hidden `.github` folder – on Mac press Cmd+Shift+. in Finder to see it).
   Commit.
3. Open the **Actions** tab → "Build APK". It runs automatically (about 5 minutes).
4. When it shows a green tick, open the run and download **PhotoTriage-apk** (a zip with `app-debug.apk`).

## Option B – build with Android Studio
1. Install Android Studio (developer.android.com/studio) and open this folder.
2. Wait for the Gradle sync to finish (accept any SDK download it suggests).
3. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. The APK is in `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the phone
Copy `app-debug.apk` to the phone (Google Drive, USB, or email it to yourself), open it,
and allow "Install unknown apps" for the app you opened it from when Android asks.
Android/Play Protect may warn that the app is from an unknown developer – that is expected
for an app you built yourself.
