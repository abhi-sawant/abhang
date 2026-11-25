# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Abhang: a fully offline Android app (Kotlin, Jetpack Compose, single `:app` module, package `com.abhang.app`) for browsing Marathi abhangs (devotional verses by Tukaram, Namdev, Dnyaneshwar, the Haripath, aartis and others). All content ships in one bundled asset, `app/src/main/assets/abhangs.json`. The app has no network access.

The repo has two halves: the Android app, and a Node content pipeline in `tools/` that generates that JSON from public-domain Marathi Wikisource text.

## Commands

Android (Gradle wrapper, AGP 9.4.1, Kotlin 2.2.10, compileSdk 37, minSdk 24):

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat test --tests "com.abhang.app.RepositorySearchTest"
.\gradlew.bat test --tests "com.abhang.app.DevanagariNormalizerTest.<testName>"
```

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

The other `tools/*.mjs` files (`analyze*`, `bigrams`, `ctx`, `misorder`, `oddchars`, `quality`, `sample`, `search`, `hpdiff`, `list`, `ws`) are ad-hoc one-off inspection scripts for the corpus, not part of the build.

## Architecture

### Runtime data flow
`MainActivity` shows a loading state, builds `AbhangRepository.load(context)` off the main thread, then hosts a Navigation Compose `NavHost` with routes `home`, `category/{id}`, `detail/{id}`, `search` and `about`. The screens are in `ui/Screens.kt` and the theme (bundled Google Sans font) in `ui/Theme.kt`.

`AbhangRepository` parses the whole JSON into memory once. Everything after that is served from memory. The JSON schema is `categories[{id,name,description}]` and `abhangs[{id,categoryId,title,author,body,source,num}]`. The models are in `data/Models.kt`. There are 9 fixed categories, defined in `tools/build_content.mjs` and emitted into the JSON, so changing them means editing the build script and regenerating, not the Kotlin.

### Search (the non-obvious part)
Search lives in `AbhangRepository` and works on two parallel lazily-built indexes, so nothing is computed at startup:
- **Devanagari path.** `DevanagariNormalizer` normalizes the text, and it's used when the query word contains Devanagari.
- **Phonetic path.** `PhoneticKey` romanizes Devanagari into a lossy key and applies the same reduction to Latin queries. The reduction drops the schwa and merges long/short vowels, aspirated/plain consonants, retroflex/dental, s/sh, v/w and doubled letters. Typing `vitthal` therefore matches `विठ्ठल`.

Each query word becomes a `Token` that picks one path or the other, so mixed-script queries work. All tokens must match (AND). A query made only of digits (ASCII or Devanagari) is treated as an abhang number (`num`) filter in `filterCategory`. When you change either normalizer, the indexes and the Latin-query behavior change together, so run `RepositorySearchTest` and `DevanagariNormalizerTest`.

### Content pipeline (`tools/` and `content/`)
1. `wslib.mjs` is the rate-limited MediaWiki API client for mr.wikisource.org. It caches raw page text in `content/raw/`, with file names that are URL-encoded titles.
2. `tukaram.mjs` and `haripath.mjs` parse the sources. `tukaram.mjs` also does automatic repair of damaged text (`repairLine`, `hasDamage`).
3. `build_content.mjs` classifies each abhang into one of the 9 categories with keyword heuristics, using the section name for folk forms and keyword lists for rupa, sant and naman. It applies modern Marathi spelling fixes (for example `modernHma`) and writes `abhangs.json`.
4. **Human-in-the-loop review.** Auto-repaired Tukaram abhangs have `source` containing `स्वयंचलित`. `make_review.mjs` samples them into a self-contained `content/review/review.html`, and the reviewer exports JSON. `apply_review.mjs` merges it into `content/corrections.json` (`{ "<id>": "corrected body" }`, which overrides the body at build time) and `content/reviewed_ok.json` (ids confirmed correct as-is). Both files are inputs to `build_content.mjs`.

Because `abhangs.json` is generated, don't hand-edit it. Fix the parser, the heuristics or `corrections.json` instead, and rebuild. Abhang ids come from the build, so `corrections.json` and `reviewed_ok.json` are keyed to them. Regenerating with different parsing logic can shift ids and silently invalidate those keys.

## Conventions

- All user-facing strings and content are Marathi (Devanagari). Keep that in UI and data.
- Content comes only from public-domain / freely licensed Wikisource sources. `app/src/main/assets/licenses/` holds license text, and `AboutScreen` credits the sources.
- `content/raw/` is a fetch cache. It's large and safe to delete and re-fetch, but re-fetching is slow because of API rate limits.
- `app/src/main/res/font/google_sans.ttf` is a subset (about 220 KB) of the full Google Sans, made by `tools/subset_font.py` from the original kept in `design/backup/`. If a new character appears in the corpus or UI strings, add its code point there and rerun. Android only needs the `wght` axis, which the subset keeps.
- This directory is not a git repository yet. `.gitignore` already excludes `.gradle/`, `.kotlin/`, `build/`, `.idea/` and `local.properties`.
