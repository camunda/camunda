# Name Your Root Project
`name_your_root_project`

**Rule:** Always set the root project's name in the settings file, so it does not depend on the checkout directory name.

---

- **Fix:** Add `rootProject.name = "my-project"` (Kotlin) or `rootProject.name = 'my-project'` (Groovy) to the settings file. Place it *after* any `pluginManagement { }` or `plugins { }` block: Gradle rejects a settings file with any statement before `plugins {}` ("only buildscript {}, pluginManagement {} and other plugins {} script blocks are allowed before plugins {} blocks"), so prepending it to a settings file that applies plugins breaks the build at startup.
- **Don't:**

  ```kotlin
  // Left empty
  ```

- **Do:**

  ```kotlin
  rootProject.name = "my-example-project"
  ```
