# Changelog

Add a new section at the top for every update, then copy it into the GitHub Release notes.

## Next
- Scroll Guard now shows today's time in X, Instagram and YouTube and how many warnings were sent (from 0 each day), and shows a pop-up over the app when time is up (needs "Display over other apps").
- Scroll Guard (Settings): a countdown starts when X, Instagram or YouTube opens and resets when you leave. Set the limit in hours and minutes; a heads-up and a time-is-up notification (with your own message and a Close button) follow. Needs Usage access, which only tells which app is open.
- In-app updates: the app checks GitHub (docs/downloads/version.json) when opened and shows an "Update" card on Home; Settings has a "Check" button. Tap Update to download, verify (SHA-256) and install.
- CI now takes the app version from the tag (v1.3 -> 1.3) and can sign with a fixed key (secrets SMRTI_KEYSTORE_BASE64, SMRTI_STORE_PASSWORD, SMRTI_KEY_ALIAS, SMRTI_KEY_PASSWORD) so updates install over the old app.

## 1.2
Speed
- Release builds are shrunk with R8 and ship a baseline profile, so the first launch and first scroll are faster.
- The splash no longer waits: it leaves on the first frame with a 140 ms fade (none when animations are reduced).
- Logos were 331/168/162 KB PNGs decoded at full size; they are now small lossless WebPs (about 27-50 KB).
- Quotes load a page at a time (Paging), so a huge list costs no more memory than a small one.
- Image grids decode thumbnails at grid size, with a placeholder and an error state.
- A notification reads only the counts and the one row it needs, instead of every image and album photo.
- "My images" use the original photos in place: no copy, no extra space, no copying time. (If Android refuses a long-term link, that one photo is copied as before.)
- Neighbour tabs are drawn only when visited; Home's clock only redraws its own status line, only while on screen.
- Notification pictures are decoded at 720 px instead of 1080 px.

New
- Reminders play Smrti's own notification sound instead of the phone's default. (Notification channel renamed to smrti_reminders_v3 because Android fixes a channel's sound once created.)
- Pick a picture for a single quote (from any album or photos). Its notification uses that picture; quotes without one get a random picture as before. Database migration 2 to 3.
- Quotes: search, category and favourites filters, edit, favourite, share, export to JSON, swipe left to delete, Undo.
- Notification buttons: Save, Share, Snooze (30 min).
- Home screen widget showing the latest quote (tap the arrow for another).
- First-run page that explains each permission; "Keep reminders on time" help for Xiaomi, Oppo/Realme/OnePlus, Vivo and Samsung.
- Limited photo access (Android 14+) is detected and explained; album photos added later appear automatically.
- The reminder page waits briefly for the picture and has a placeholder.
- Reduce motion setting; buttons and tabs grow with large font sizes; tablet and landscape layouts (readable width, more grid columns).
- Hindi translation; all text now lives in strings.xml.
- 74 starter quotes (was 2). Existing phones get the new ones added once; nothing is removed.
- Crash log (Settings, About) that can be shared; unit tests; release signing via keystore.properties.

Safe upgrade
- Database moved to version 2 with a real migration (adds favourites). Future table changes must add a Migration.
- Restored phones: pictures whose file or link no longer exists are dropped on start; backups skip copied photos.
- An import can no longer leave an orphan file if you leave the screen; leftover files are cleaned up on start.

## 1.1.1
- Fixed: the app sometimes closed right after tapping a reminder notification, or Android showed an "app has a bug" message. Splash, notification posting, alarm scheduling and the tap handler can no longer crash the app, and the activity now reuses the open instance (singleTop).

## 1.1
- Light and dark theme (Settings → Appearance: System, Light or Dark). Dark stays the default.
- Tapping a reminder opens it as a full page inside the app instead of a small pop-up.
- No more pop-up inputs: adding a quote, importing JSON, choosing albums, the About page, a custom interval, quiet-hours times and delete confirmation all happen inside the app.
- Peak-end design: an opened reminder reveals its picture, words and author one after another, and both finishing a reminder and saving a quote end on a warm closing moment.
- Friendly empty screens (quotes, images, albums, home) with a clear next step.
- Animated splash: the logo pops and fades into the app.
- Images tab shows the photos from the albums you picked, and Random / In series is now a small drop-down.

## 1.0
- Reminders every 1 min to 7 days with quotes, motivation and dialogues.
- Pop-up banner on the home screen and visible on the lock screen; tapping opens the quote in the app.
- Your own images or whole phone albums on each reminder, shown at random or in series.
- Add-quote card with live preview, categories and an emoji picker.
- Floating expandable bottom bar and swipe between tabs.
- Quiet hours, category filters, About page.
