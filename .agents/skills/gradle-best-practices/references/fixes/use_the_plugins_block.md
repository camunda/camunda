# Apply Plugins Using the `plugins` Block
`use_the_plugins_block`

**Rule:** Always apply plugins with the `plugins {}` block.

---

- **Fix:** Replace with `plugins { id("...") }` (add `version "..."` for external plugins) and delete the corresponding `buildscript { }` classpath entry.
- **Don't:**

  ```kotlin
  buildscript {
      repositories {
          gradlePluginPortal()
      }

      dependencies {
          classpath("com.google.protobuf:com.google.protobuf.gradle.plugin:0.9.4")
      }
  }

  apply(plugin = "java")
  apply(plugin = "com.google.protobuf")
  ```

- **Do:**

  ```kotlin
  plugins {
      id("java")
      id("com.google.protobuf").version("0.9.4")
  }
  ```
