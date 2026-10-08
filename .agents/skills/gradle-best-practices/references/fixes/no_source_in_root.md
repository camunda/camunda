# Do Not Put Source Files in the Root Project
`no_source_in_root`

**Rule:** The root project should carry shared settings and conventions, not source. Put source in subprojects.

---

- **Fix:** Create a subproject (e.g. `core/` or `lib/`), move `src/` into it, give it its own build script with the language plugin, and add `include("core")` to the settings file. Leave the root script with only shared configuration. Structural — plan first, move incrementally, keep the build green.
