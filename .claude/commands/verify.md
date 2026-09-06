---
description: Build and test sanity check before reporting a change complete.
---

**Before Phase 0 lands** (no Gradle wrapper exists yet): there is nothing
to build or test. Say so explicitly rather than fabricating a result.

**Once the Android Studio / Gradle scaffold exists**, run these in order
and report the results:

1. Unit tests:
   ```
   ./gradlew test
   ```
2. Debug build (catches Compose/compile errors the IDE might not surface
   in a quick edit):
   ```
   ./gradlew assembleDebug
   ```
3. If the change touched Room entities/DAOs, confirm a migration exists
   (or that `fallbackToDestructiveMigration` is still intentional for this
   pre-release stage) rather than assuming Room will silently handle a
   schema change.

If this repo has no lint/format tool configured at the time you're
reading this (check for a ktlint/detekt config before assuming), don't
invent one — matches the "don't add tooling that isn't there" convention
from this project's sibling repo,
[coding-adventure](https://github.com/shreejitpanchal/coding-adventure).

If anything fails, investigate and fix the root cause rather than
skipping or loosening the failing check.
