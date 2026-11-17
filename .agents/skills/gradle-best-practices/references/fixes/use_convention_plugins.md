# Use Convention Plugins for Common Build Logic
`use_convention_plugins`

**Rule:** Put shared build logic in reusable convention plugins instead of duplicating configuration across build scripts.

---

- **Fix:** Extract to a precompiled script plugin in `build-logic/src/main/kotlin/` (e.g. `my.java-library.gradle.kts`), compose small plugins rather than one large one, and apply with `plugins { id("my.java-library") }`.
