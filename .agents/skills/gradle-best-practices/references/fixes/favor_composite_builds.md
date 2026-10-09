# Favor `build-logic` Composite Builds for Build Logic
`favor_composite_builds`

**Rule:** Put custom plugins and shared build logic in an included composite build (conventionally `build-logic/`) rather than in `buildSrc/`.

---

- **Fix:** Create `build-logic/` with its own `settings.gradle(.kts)` and a `plugin/` project, move the sources across, declare the plugins with `gradlePlugin { plugins { create("myPlugin") { id = ...; implementationClass = ... } } }`, and add `includeBuild("build-logic")` to the root settings file. Structural.
