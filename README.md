# Smrti (स्मृति)

Quotes, motivation and dialogues with your own pictures, on your lock screen. Android app by Microintel.

| | |
|---|---|
| **Website + app download** | https://microintel.github.io/Smrti/ |
| **Changelog** | [CHANGELOG.md](CHANGELOG.md) |

People download the app **from the website**, not from GitHub. The APK file is hosted by the site itself.

## What is in this repo

```
Smrti/
├── app/                        Android app source (Kotlin, Jetpack Compose)
├── build.gradle.kts            Gradle project files
├── settings.gradle.kts
├── gradle.properties
├── docs/                       The website (GitHub Pages serves this folder)
│   ├── index.html
│   ├── assets/                 Logos
│   └── downloads/
│       ├── Smrti-latest.apk    The file the Download button gives out
│       └── version.json        Version, date and notes shown on the page
├── scripts/publish-apk.sh      Adds a new APK + version info to the site
├── .github/workflows/
│   └── build-apk.yml           Optional: GitHub builds the APK and publishes it to the site
├── CHANGELOG.md
└── README.md
```

## Put it on GitHub (first time)

1. Create a new repository on GitHub named `Smrti` (no README, since this folder has one).
2. In this folder:
   ```bash
   git init
   git add .
   git commit -m "Smrti 1.0"
   git branch -M main
   git remote add origin https://github.com/microintel/Smrti.git
   git push -u origin main
   ```
3. **Turn on the website:** repo → *Settings* → *Pages* → Source: *Deploy from a branch* → Branch `main`, folder `/docs` → Save.
   After a minute it is live at `https://microintel.github.io/Smrti/`.
4. **Add the app to the website:** build the APK in Android Studio (*Build → Build APK(s)*, the file is in
   `app/build/outputs/apk/debug/`), then:
   ```bash
   ./scripts/publish-apk.sh app/build/outputs/apk/debug/app-debug.apk 1.0 "First release"
   git add docs && git commit -m "Add Smrti 1.0 APK" && git push
   ```
   No script? Copy the file to `docs/downloads/Smrti-latest.apk`, edit `docs/downloads/version.json`, commit and push.
   Until the APK is added, the page shows "Download coming soon".

## Releasing an update

1. Make your changes and test.
2. In `app/build.gradle.kts` raise `versionCode` by 1 and change `versionName` (for example `1.1`).
3. Add a section to `CHANGELOG.md`.
4. Build the APK and run `./scripts/publish-apk.sh <apk> 1.1 "What's new"`, then commit and push.
   Or let GitHub do it: `git push`, then `git tag v1.1 && git push origin v1.1`. The *Build APK for website*
   workflow builds the APK and updates `docs/downloads` for you.

The page shows the new version, date, notes, file size and SHA-256 by itself.

Files on GitHub Pages can be up to 100 MB each, far above a normal APK. Pages sites are public.

### About updating installed copies
Android only installs an update over an existing copy if both APKs are signed with the **same key**.
Debug builds use a key that differs between computers (and on GitHub's build servers), so people may have to
uninstall before installing a build from a different source. For smooth updates, build every release with one
release keystore. Keep that keystore safe and never commit it.

---

# App details

Hourly quote, motivation and dialogue notifications with your own images.
Kotlin, Jetpack Compose, Room (SQLite), AlarmManager. Min Android 9 (API 28).

## Build
1. Open this folder in Android Studio (Ladybug or newer) and let Gradle sync.
2. Run on a phone or emulator. Allow notifications when asked.

## Your 50 quotes
Either replace `app/src/main/assets/quotes.json` before the first install,
or use **Quotes → Import JSON** in the app at any time (duplicates are skipped).

Accepted formats:
```json
[ { "text": "…", "author": "…", "category": "Quote | Motivation | Dialogue" } ]
```
also `["plain string", …]` and `{ "quotes": [ … ] }`.
Field aliases: text/quote/content/message/dialogue, author/by/name/speaker/character/source, category/type/tag.

## How it works
- Interval: 1 min, 10 min, 1 hr, 5 hr or Custom (1 minute to 7 days), chosen on the Home tab.
- Delivery: a self-rescheduling alarm (`Scheduler`, `ReminderReceiver`) fires each reminder, then sets the next. It is restored after reboot and app update (`BootReceiver`).
- Quote choice: least recently shown first, random among never-shown, so all quotes rotate before any repeats.
- Images: picked in the Images tab, copied into app storage, one image is attached to each notification, either at random or in series (Images tab → Show images). Whole phone albums can be added too (Images tab → Phone albums → Choose; needs photo access); in series mode the images of "my images" then the chosen albums are shown one after another and loop. It is shown without expanding: Android 12+ uses the system's collapsed big-picture mode, Android 11 and older use a custom wide banner (Settings → Large image banner). No images = text only.
- Notifications pop up as a banner over the home screen and other apps (high-importance channel `smrti_reminders_v2`) and show on the lock screen (public visibility). Tapping one opens Smrti and shows that exact quote and picture in a card (`ReminderDialog`).
- Settings: categories to include, images on/off, quiet hours, notification and precise-timing shortcuts, and an About Smrti page (version, what it does, developed by Microintel).
- Navigation: floating pill bar (selected tab expands with its label) and swipe left/right between tabs (HorizontalPager); hides while the keyboard is open.
- Look: true black (#000000) background for OLED screens, orange accent from the logo.
- Splash screen: AndroidX SplashScreen with the Smrti mark on black.
- Logo: launcher icon (adaptive + themed), notification icon and in-app logo all come from your Smrti image.

## Importing quotes
Quotes → Import JSON first shows a guide (what file, the steps, the exact structure with an example, which fields are required) with a Copy example button, then Choose file opens the picker.

## Adding quotes
Quotes → Add quote opens a card with a live preview, category pills (💬 Quote, 🔥 Motivation, 🎬 Dialogue), a character count and a tap-to-insert emoji strip (emoji go in at the cursor). The keyboard's own emoji work too, and emoji appear in the notification.

## Notes
- For exact short intervals, allow "Alarms & reminders" via Settings → Precise timing.
  Without it Android may delay reminders by a few minutes. In deep sleep (Doze) Android also limits
  very frequent alarms, so a 1-minute interval is best-effort when the phone is idle.
- For best timing set Smrti's battery usage to Unrestricted.
- Every reminder alerts (banner + sound), even at short intervals; each replaces the previous one in the shade. To silence a channel, use Settings → Notifications.
- Lock screen content can be hidden by the phone's own "Sensitive notifications" / "Lock screen" setting; Smrti marks it public.


## Release build and signing

```
# unsigned (R8 + baseline profile)
gradle assembleRelease

# signed: create keystore.properties next to build.gradle.kts (never commit it)
storeFile=../smrti-release.jks
storePassword=...
keyAlias=...
keyPassword=...
gradle assembleRelease
```
CI can use the environment variables SMRTI_STORE_FILE, SMRTI_STORE_PASSWORD, SMRTI_KEY_ALIAS and SMRTI_KEY_PASSWORD instead.

## Tests
`gradle testDebugUnitTest` runs the importer, formatting and migration checks.

## Changing the database
Bump `version` in `Database.kt` and add a `Migration` to `ALL_MIGRATIONS`. Never edit an old one.
