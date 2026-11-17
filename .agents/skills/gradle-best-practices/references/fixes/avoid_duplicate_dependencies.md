# Avoid Redundant Dependency Declarations
`avoid_duplicate_dependencies`

**Rule:** Do not declare the same module more than once, or in two configurations where one already implies the other.

---

- **Fix:** Keep the single declaration with the widest correct scope (`api` wins over `implementation` for a module exposed in the public API) and delete the rest.
- **Don't:**

  ```kotlin
  plugins {
      `java-library`
  }

  dependencies {
      api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0")
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0")
  }
  ```

- **Do:**

  ```kotlin
  plugins {
      `java-library`
  }

  dependencies {
      api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0")
  }
  ```
