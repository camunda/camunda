# Don't hardcode Task names unless they are documented as Public API
`dont_hardcode_task_names`

**Rule:** Prefer, in this order: (1) the plugin's own DSL extension, (2) the task *type*, (3) a task name - and only when that name is explicitly documented as public API. Most task names are internal details that may be renamed or removed.

---

- **Fix:** Best, configure through the plugin's own DSL (`java { }`, `publishing { }`) and don't reach for the task at all. Failing that, replace the literal with the public constant — `tasks.named<JavaCompile>(JavaPlugin.COMPILE_JAVA_TASK_NAME)` — or configure by type.
- **Don't:**

  ```kotlin
  plugins {
      id("java-library")
      id("maven-publish")
  }

  tasks.named<JavaCompile>("compileJava").configure {
      sourceCompatibility = "17"
      targetCompatibility = "17"
  }

  publishing {
      publications {
          create<MavenPublication>("maven") {
              from(components["java"])
          }
          // ...
  ```

- **Do:**

  ```kotlin
  tasks.named<JavaCompile>(JavaPlugin.COMPILE_JAVA_TASK_NAME) {
      sourceCompatibility = "17"
      targetCompatibility = "17"
  }
  ```
