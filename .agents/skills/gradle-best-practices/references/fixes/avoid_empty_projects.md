# Avoid Unintentionally Creating Empty Projects
`avoid_empty_projects`

**Rule:** With nested directory layouts, set `projectDir` explicitly so Gradle does not synthesize empty intermediate projects.

---

- **Fix:**

  ```kotlin
  include(":my-web-module")
  project(":my-web-module").projectDir = file("subs/web/my-web-module")
  ```

  Invocation then becomes `gradle :my-web-module:build`.
- **Don't:**

  ```kotlin
  include(":app")
  include(":subs:web:my-web-module")
  ```

- **Do:**

  ```kotlin
  include(":app")

  include(":my-web-module")
  project(":my-web-module").projectDir = file("subs/web/my-web-module")
  ```
