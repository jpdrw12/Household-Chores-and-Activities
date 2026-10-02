# Releasing

## One-time setup

A release keystore (`release.keystore.jks`) and its passwords (`keystore.properties`) were
generated locally and are gitignored — **back both up somewhere durable** (a password manager,
an encrypted drive). If this keystore is ever lost, every future release becomes a different,
unrelated signing identity — Android treats that as a different app for update purposes.

To let GitHub Actions build signed releases, add these repo secrets (Settings → Secrets and
variables → Actions → New repository secret):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.keystore.jks` |
| `RELEASE_STORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `RELEASE_KEY_PASSWORD` | `keyPassword` from `keystore.properties` (same value — PKCS12) |
| `GOOGLE_SERVICES_JSON` | Full contents of `app/google-services.json` (optional — omit to build without Firebase sync) |

## Cutting a release

```bash
./bump-version.sh [patch|minor|major]   # bumps versionName/versionCode in app/build.gradle.kts
# edit CHANGELOG.md: move [Unreleased] items under a new [x.y.z] - date heading
git add app/build.gradle.kts CHANGELOG.md
git commit -m "Bump to x.y.z"
git push origin main
./tag-release.sh                        # tags v<version> and pushes it
```

Pushing the tag triggers `.github/workflows/release.yml`, which builds a signed `assembleRelease`
APK and attaches it to a GitHub Release at that tag. Watch progress under the repo's Actions tab.

## Installing

From the repo's Releases page, download the `.apk` attached to the latest release and open it —
Android will prompt to enable "install unknown apps" for whichever app you downloaded it with
(browser, file manager), once. There's no Play Store listing; this is sideloaded.

The app also checks for updates itself (see `AppUpdateChecker.kt`) and can prompt to download and
install a newer release directly, same mechanism, triggered from Admin · Stats.
