# Don't Assume your Plugin is Applied after Another
`dont_assume_plugin_order`

**Rule:** Do not write build logic that depends on a particular plugin application order.

---

- **Fix:** Wrap in `project.pluginManager.withPlugin("plugin-id") { ... }`, or apply the prerequisite explicitly with `project.pluginManager.apply("plugin-id")`.

  **A conditional is not a guard.** `if (project.plugins.hasPlugin("java-library")) { … }` looks like the fix and is the bug: `hasPlugin` answers *has it been applied by now*, so applying this plugin first yields `false`, the block never runs, and nothing is logged or thrown — the plugin just configures nothing. Same for `pluginManager.hasPlugin(…)` and `plugins.findPlugin(…)`.

  ```kotlin
  // Don't — eager test, silently skipped when this plugin is applied first
  if (project.plugins.hasPlugin("java-library")) { configureJavaModule(project) }

  // Do — deferred callback, fires in either application order
  project.pluginManager.withPlugin("java-library") { configureJavaModule(project) }
  ```

- **Don't:**

  ```kotlin
  // build.gradle.kts
  subprojects {
      // Apply the Java plugin to every subproject
      afterEvaluate {
          // This runs after the app subproject’s build script is evaluated and results in an error
          pluginManager.apply("java")
      }
  }
  ```

  ```kotlin
  // app/build.gradle.kts
  plugins {
      id("myplugin")
  }
  // Assumes 'java' plugin is present
  extensions.getByType<org.gradle.api.plugins.JavaPluginExtension>().apply {
      toolchain.languageVersion.set(JavaLanguageVersion.of(21))
  }
  ```

  ```kotlin
  // buildSrc/src/main/kotlin/MyPlugin.kt
  class MyPlugin : Plugin<Project> {
      override fun apply(project: Project) {
          // Assumes 'java' plugin is present
          // WARNING: This will fail if the 'java' plugin hasn't been applied yet.
          project.extensions.getByType(JavaPluginExtension::class.java).toolchain {
              languageVersion.set(JavaLanguageVersion.of(21))
          }
      }
  }
  ```

- **Do:**

  ```kotlin
  // app/build.gradle.kts
  pluginManager.withPlugin("java") {
      extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
          toolchain.languageVersion.set(JavaLanguageVersion.of(21))
      }
  }
  ```

  ```kotlin
  // buildSrc/src/main/kotlin/MyPlugin.kt
  class MyPlugin : Plugin<Project> {
      override fun apply(project: Project) {
          // If your plugin requires 'java', apply it so order doesn’t matter
          project.pluginManager.apply("java")
          // Now it's safe to configure Java things immediately
          project.extensions.configure(JavaPluginExtension::class.java) {
              toolchain.languageVersion.set(JavaLanguageVersion.of(21))
          }
      }
  }
  ```
