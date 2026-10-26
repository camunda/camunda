# Avoid `afterEvaluate`
`avoid_after_evaluate`

**Rule:** Do not use `project.afterEvaluate {}` to configure tasks, wire properties, or react to plugin application.

---

- **Fix:** Use lazy `Property<T>` / `Provider<T>` wiring so values are read at execution time, and `pluginManager.withPlugin("plugin-id") { }` to react to plugin application.
- **Don't:**

  ```kotlin
  plugins {
      id("java-library")
      id("app-info-plugin")
  }

  afterEvaluate {
      the<AppInfoExtension>().appName.set("my-app")
  }
  ```

- **Do:**

  ```kotlin
  plugins {
      id("java-library")
      id("app-info-plugin")
  }

  appInfo {
      appName.set("my-app")
  }
  ```
