# Use unique output files and directories
`use_unique_output_files_and_directories`

**Rule:** Give each task its own output location, so a sibling task writing to a shared directory does not invalidate it.

---

- **Fix:** Give each a distinct path, or switch to `@OutputFile` with distinct file names: `layout.buildDirectory.dir("greetings").map { it.file("a.txt") }`.
- **Don't:**

  ```kotlin
  tasks.register<GreetingTask>("greeterA") {
      type = "a"
      outputDirectory = layout.buildDirectory.dir("greetings")
  }
  tasks.register<GreetingTask>("greeterB") {
      type = "b"
      outputDirectory = layout.buildDirectory.dir("greetings")  // same directory
  }
  ```

- **Do:**

  ```kotlin
  tasks.register<GreetingTask>("greeterA") {
      type = "a"
      outputDirectory = layout.buildDirectory.dir("greetings")
  }
  tasks.register<GreetingTask>("greeterB") {
      type = "b"
      outputDirectory = layout.buildDirectory.dir("greetings-2")
  }
  ```
