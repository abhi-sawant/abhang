# Changelog

## 1.2.0 — 2026-11-25

### Added

- The app now updates itself. When the phone is online it checks for a new version twice a day and downloads it. On Android 12 and newer the update usually installs by itself while the app is closed. Otherwise a large "आत्ता अद्ययावत करा" (update now) button on the home screen installs it with one tap. The first time, the app asks once to allow "Install unknown apps". You can also check from माहिती व स्रोत (About).
- Search forgives small mistakes. It ignores spaces, so `सुंदरते` or `sundarte` finds `सुंदर तें`, and it allows one typo in words of 5 or more letters (two in words of 9 or more). Results with the fewest mistakes come first, and the matched words are highlighted.

### Changed

- The app now uses the internet, but only to check for and download new versions from this project's GitHub Releases. All abhangs stay on the phone and work offline. Nothing is collected or sent anywhere.

### Upgrading from 1.1.1

- Version 1.1.1 has no updater. Install 1.2.0 over it by hand once; after that, updates arrive on their own.

## 1.1.1 — 2026-11-25

First release of Abhang (अभंग संग्रह), an offline Android app for reading Marathi abhangs.

### Added

- 4,476 abhangs and verses bundled in the app, arranged in 9 categories: the Tukaram gatha, Namdev's abhangs, Dnyaneshwar's Haripath and Pasaydan, the Vitthal and Ganpati aartis, and the gajar chants.
- Works fully offline. The app has no internet permission.
- Search in Devanagari or in English letters: typing `vitthal` finds `विठ्ठल`. Results show the matching line, with the matching words highlighted.
- Voice search in Marathi, using your phone's speech recognizer. It needs internet unless an offline Marathi voice pack is installed.
- Filter a category by title or by abhang number.
- Larger or smaller reading text (अ− / अ+). The screen stays on while you read.
- Favourites (★), and "continue reading" on the home screen to reopen the last abhang.
- Share an abhang's text with its number.
- Light and dark themes that follow your phone's setting.
- Each abhang shows where its text comes from. Text repaired automatically is marked as needing review, and texts that vary between traditions (Haripath, Pasaydan, aartis) carry a note to check them against your own tradition.
- An About screen that credits every source, mainly Marathi Wikisource.
