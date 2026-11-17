# Don't resolve Configurations before Task Execution
`dont_resolve_configurations_before_task_execution`

**Rule:** Do not resolve a configuration during the configuration phase; pass the configuration itself to the task input so dependency information survives.

---

- **Fix:** Declare `@InputFiles abstract val classpath: ConfigurableFileCollection` and wire `classpath.from(configurations.named("runtimeClasspath"))`.
- **Don't:**

  ```kotlin
      // ...
      runtimeOnly(project(":lib"))
  }

  abstract class BadClasspathPrinter : DefaultTask() {
      @get:InputFiles
      var classpath: Set<File> = emptySet()

      private fun calculateDigest(fileOrDirectory: File): Int {
          require(fileOrDirectory.exists()) { "File or directory $fileOrDirectory doesn't exist" }
      // ...
      }
  }

  tasks.register("badClasspathPrinter", BadClasspathPrinter::class) {
      classpath = configurations.named("runtimeClasspath").get().resolve()
  }
  ```

- **Do:**

  ```kotlin
      // ...
      runtimeOnly(project(":lib"))
  }

  abstract class GoodClasspathPrinter : DefaultTask() {
      @get:InputFiles
      abstract val classpath: ConfigurableFileCollection

      private fun calculateDigest(fileOrDirectory: File): Int {
          require(fileOrDirectory.exists()) { "File or directory $fileOrDirectory doesn't exist" }
      // ...
      }
  }

  tasks.register("goodClasspathPrinter", GoodClasspathPrinter::class.java) {
      classpath.from(configurations.named("runtimeClasspath"))
  }
  ```
