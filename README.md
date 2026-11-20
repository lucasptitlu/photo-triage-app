# Photo Triage

Find groups of similar photos, keep the best shot, remove the rest.
Everything runs on the user's own device: photos are never uploaded.

## What's in this repository

| Path | What it is |
|---|---|
| `web/index.html` | **Desktop web app** (the site's home page) |
| `web/download/` | **Download page**: Android APK + iPhone instructions |
| `web/mobile/` | **Phone web app** (used on iPhone, optional on Android) |
| `app/`, `build.gradle`, `settings.gradle`, `gradle/` | **Android app** source |
| `.github/workflows/build-apk.yml` | Builds the APK on every Android change and publishes it to `web/download/PhotoTriage.apk` |
| `netlify.toml` | Tells Netlify to publish the `web` folder |

## Hosting on Netlify
1. Netlify → **Add new site → Import an existing project → GitHub** → pick this repository.
2. Leave the settings as they are (`netlify.toml` provides them) and click **Deploy**.
3. Every push to GitHub redeploys the site, including each new APK published by the workflow.

## Updating the Android app
Change anything under `app/` and push. GitHub Actions builds the APK (about 2 minutes),
commits it to `web/download/PhotoTriage.apk`, and Netlify publishes it.
You can also run it by hand: **Actions → Build APK → Run workflow**.

The APK is signed with `app/debug.keystore`, so new versions install over old ones.
It's a convenience key for a hobby app: anyone with access to this repository can sign with it.

## Browser support (desktop)
- **Chrome / Edge**: full features, including moving rejects into a `_Rejected` subfolder.
- **Firefox / Safari**: grouping and review work; at the end you get the list of files to remove.
