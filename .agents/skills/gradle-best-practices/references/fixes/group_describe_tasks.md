# Group and Describe custom Tasks
`group_describe_tasks`

**Rule:** Give every custom task a `group` and a `description` so it is discoverable in the task report.

---

- **Fix:** Set both, either in the registration block or as defaults in the task class constructor:
  `group = "documentation"`, `description = "Generates project documentation from source files."`
- **Don't:**

  ```kotlin
  tasks.register("generateDocs") {
      // Build logic to generate documentation
  }
  ```

- **Do:**

  ```kotlin
  tasks.register("generateDocs") {
      group = "documentation"
      description = "Generates project documentation from source files."
      // Build logic to generate documentation
  }
  ```
