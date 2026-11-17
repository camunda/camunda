# Don't Explicitly Depend on the Kotlin Standard Library
`dont_depend_on_kotlin_stdlib`

**Rule:** Omit an explicit stdlib dependency - the Kotlin Gradle Plugin adds the matching version itself.

---

- **Fix:** Delete the declaration.
- **Don't:**

  ```kotlin
  plugins {
      kotlin("jvm").version("2.4.0")
  }

  dependencies {
      api(kotlin("stdlib"))
  }
  ```

- **Do:**

  ```kotlin
  plugins {
      kotlin("jvm").version("2.4.0")
  }
  ```
