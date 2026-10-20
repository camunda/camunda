# Use Kotlin DSL
`use_kotlin_dsl`

**Rule:** Prefer the Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`) over the Groovy DSL for type safety and IDE support.

---

- **Fix:** Migrate the script to `.gradle.kts`. This is a structural fix — describe the plan and convert incrementally, one script at a time, confirming the build still succeeds after each.
