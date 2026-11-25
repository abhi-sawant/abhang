# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Abhang: a fully offline Android app (Kotlin, Jetpack Compose, single `:app` module, package `com.abhang.app`) for browsing Marathi abhangs (devotional verses by Tukaram, Namdev, Dnyaneshwar, the Haripath, aartis and others). All content ships in one bundled asset, `app/src/main/assets/abhangs.json`. The app has no `INTERNET` permission. The only network-related piece is voice search: it hands off to the system speech recognizer, and it uses `ACCESS_NETWORK_STATE` only to warn when the phone is offline.

The repo has two halves: the Android app, and a Node content pipeline in `tools/` that generates that JSON. Most text comes from Marathi Wikisource; the Haripath and the aartis come from a few other public pages (see "Content pipeline").

## Commands

Android (Gradle wrapper, AGP 9.4.1, Kotlin 2.2.10, compileSdk 37, minSdk 24):

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat test --tests "com.abhang.app.RepositorySearchTest"
.\gradlew.bat test --tests "com.abhang.app.DevanagariNormalizerTest.<testName>"
```

`java` is not on PATH on the dev machine. Set `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` (JDK 25) before running Gradle.

Unit tests are plain JVM tests. They use `org.json:json` as a test dependency, and `unitTests.isReturnDefaultValues = true` stubs Android APIs. Don't add instrumented dependencies for logic that can be tested on the JVM.

Release builds enable R8 minification and resource shrinking (`app/proguard-rules.pro`). There is no lint or format task beyond `kotlin.code.style=official`.

Content pipeline (plain Node ESM `.mjs`, no package.json, no dependencies; needs Node 18+ for global `fetch`):

```powershell
node tools/fetch_all.mjs              # download Wikisource pages into content/raw/ (cached; skips existing files)
node tools/build_content.mjs          # regenerate app/src/main/assets/abhangs.json
node tools/build_content.mjs --report # same, with a classification report
node tools/make_review.mjs 150        # build content/review/review.html (sample of auto-repaired abhangs)
node tools/apply_review.mjs <review_results.json>  # merge review output into corrections.json / reviewed_ok.json
```

The other `tools/*.mjs` files (`analyze*`, `bigrams`, `ctx`, `misorder`, `oddchars`, `quality`, `sample`, `search`, `hpdiff`, `list`, `ws`, `parse_tukaram`, `_norm`) are ad-hoc one-off inspection scripts for the corpus, not part of the build. `parse_tukaram.mjs` is an older standalone parser; the build uses `tukaram.mjs`.

## Architecture

### Runtime data flow
`MainActivity` shows a loading state, builds `AbhangRepository.load(context)` off the main thread, then calls `repo.warmUp()` on a background dispatcher to build the search indexes. It hosts a Navigation Compose `NavHost` with routes `home`, `category/{id}`, `detail/{id}`, `favorites`, `search` and `about`. The screens are in `ui/Screens.kt`, voice search (system `RecognizerIntent` in `mr-IN`, with an offline warning dialog) in `ui/VoiceSearch.kt`, and the theme (bundled Google Sans font, light and dark color schemes that follow the system) in `ui/Theme.kt`.

Per-device state lives in SharedPreferences. `data/ReadingStore.kt` (prefs file `reading`) keeps the last-read abhang, which home shows as "continue reading", and the favourites list. The reading font size is stored under `font_size` in the `settings` prefs, set by the अ−/अ+ buttons in `Screens.kt`. The detail screen keeps the screen on while open, shares the abhang text with a number footer, and shows a provenance badge from `source`: `(स्वयंचलित…)` for auto-repaired, `(तपासले)` for reviewed, `(पाठ तपासणे आवश्यक)` for texts that vary between traditions. The About screen shows the installed `versionName`.

`AbhangRepository` parses the whole JSON into memory once. Everything after that is served from memory. The JSON schema is `categories[{id,name,description}]` and `abhangs[{id,categoryId,title,author,body,source,num}]`. The models are in `data/Models.kt`. There are 9 fixed categories, defined in `tools/build_content.mjs` and emitted into the JSON, so changing them means editing the build script and regenerating, not the Kotlin.

### Search (the non-obvious part)
Search lives in `AbhangRepository` and works on two parallel indexes. They are `lazy`, so loading stays fast, and `MainActivity` builds them right after load via `warmUp()` so the first query is instant:
- **Devanagari path.** `DevanagariNormalizer` normalizes the text, and it's used when the query word contains Devanagari.
- **Phonetic path.** `PhoneticKey` romanizes Devanagari into a lossy key and applies the same reduction to Latin queries. The reduction drops the schwa and merges long/short vowels, aspirated/plain consonants, retroflex/dental, s/sh, v/w and doubled letters. Typing `vitthal` therefore matches `विठ्ठल`.

Each query word becomes a `Token` that picks one path or the other, so mixed-script queries work. All tokens must match (AND). Full search (`searchWithTotal`) matches title, author, category name and body, ranks title and exact-word hits first, and caps results at 300 while reporting the total. The category filter (`filterCategory`) matches only title and author, and a query made only of digits (ASCII or Devanagari) filters by abhang number (`num`). When you change either normalizer, the indexes and the Latin-query behavior change together, so run `RepositorySearchTest` and `DevanagariNormalizerTest`.

### Content pipeline (`tools/` and `content/`)
1. `wslib.mjs` is the rate-limited MediaWiki API client for mr.wikisource.org. It caches raw page text in `content/raw/`, with file names that are URL-encoded titles.
2. `tukaram.mjs` and `haripath.mjs` parse the sources. `tukaram.mjs` also does automatic repair of damaged text (`repairLine`, `hasDamage`). `haripath.mjs` reads `content/raw/hp_mahiti.txt` and `hp_chakradeo.txt`, which were saved by hand from mahitidarshak.com and amit.chakradeo.net. `fetch_all.mjs` does not download them. The `ar_*` files next to them are the saved aarti pages.
3. `build_content.mjs` assembles the Tukaram gatha and Namdev abhangagatha (Wikisource), the Haripath, and the Pasaydan (Wikisource). The two aartis and the short gajar chants are typed directly into the script. It classifies each abhang into one of the 9 categories with keyword heuristics, using the section name for folk forms and keyword lists for rupa, sant and naman. It applies modern Marathi spelling fixes (for example `modernHma`) and writes `abhangs.json`.
4. **Human-in-the-loop review.** Auto-repaired Tukaram abhangs have `source` containing `स्वयंचलित`. `make_review.mjs` samples them into a self-contained `content/review/review.html`, and the reviewer exports JSON. `apply_review.mjs` merges it into `content/corrections.json` (`{ "<id>": "corrected body" }`, which overrides the body at build time) and `content/reviewed_ok.json` (ids confirmed correct as-is). Both files are inputs to `build_content.mjs`, which changes the source marker of those ids to `(तपासले)`.

Because `abhangs.json` is generated, don't hand-edit it. Fix the parser, the heuristics or `corrections.json` instead, and rebuild. Abhang ids come from the build, so `corrections.json` and `reviewed_ok.json` are keyed to them. Regenerating with different parsing logic can shift ids and silently invalidate those keys.

## Releasing

- `VERSION` (repo root, `X.Y.Z`) is the single source of the app version. `app/build.gradle.kts` reads it for `versionName` and derives `versionCode = X*10000 + Y*100 + Z`, so each part must be 0..99.
- To release, run the `/release <X.Y.Z>` skill (`.claude/skills/release/SKILL.md`). It bumps `VERSION`, adds a `## X.Y.Z — YYYY-MM-DD` section to `CHANGELOG.md` and runs the checks. It does not commit.
- Pushing a `VERSION` change to `main` runs `.github/workflows/release.yml`. When no tag `X.Y.Z` exists yet, it tests, builds and signs the APK, checks the signing certificate against the pinned `EXPECTED_CERT_SHA256`, and creates the GitHub Release `X.Y.Z` with `abhang-X.Y.Z.apk`, `SHA256SUMS.txt` and the CHANGELOG section as notes. If the tag already exists, the workflow does nothing.
- Release signing: locally from the git-ignored `keystore.properties` plus `abhang-upload.jks` in the repo root. In CI it comes from the `ABHANG_KEYSTORE_BASE64`, `ABHANG_KEYSTORE_PASSWORD`, `ABHANG_KEY_ALIAS` and `ABHANG_KEY_PASSWORD` secrets. Without a key the release build is unsigned (`app-release-unsigned.apk`). Never commit the key, and never replace it: Android only installs updates signed with the same certificate.

## Conventions

- All user-facing strings and content are Marathi (Devanagari). Keep that in UI and data.
- Content is traditional public-domain text. Wikisource material is CC BY-SA. `AboutScreen` credits every source (Wikisource, mahitidarshak.com, shubhpanchang.com, amit.chakradeo.net), so add a credit there when adding a source. `app/src/main/assets/licenses/` currently holds only the font license.
- `content/raw/` is git-ignored. Its URL-encoded Wikisource files are a fetch cache that `fetch_all.mjs` can rebuild, though slowly because of API rate limits. The `hp_*` and `ar_*` files were saved by hand and cannot be re-fetched, so don't delete them; without them the Haripath can't be rebuilt.
- `app/src/main/res/font/google_sans.ttf` is a subset (about 220 KB) of the full Google Sans, made by `tools/subset_font.py` from the original kept in `design/backup/`. If a new character appears in the corpus or UI strings, add its code point there and rerun. Android only needs the `wght` axis, which the subset keeps.
- `.gitignore` excludes `.gradle/`, `.kotlin/`, `build/`, `.idea/`, `local.properties`, `content/raw/`, and the signing files (`keystore.properties`, `*.jks`, `*.keystore`, `abhang-signing-backup*.zip`).
