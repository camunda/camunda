# Use `@PathSensitivity.NONE` for file inputs and `@PathSensitivity.RELATIVE` for directories
`default_path_sensitivities`

**Rule:** Annotate file inputs `@PathSensitive(PathSensitivity.NONE)` and directory inputs `@PathSensitive(PathSensitivity.RELATIVE)`, so absolute paths do not defeat up-to-date checks and caching.

---

- **Fix:** `@InputFile @PathSensitive(PathSensitivity.NONE) abstract val candidatesFile: RegularFileProperty`, and `RELATIVE` for `@InputDirectory`.
- **Don't:**

  ```kotlin
  abstract class AnimalSearchTask : DefaultTask() {
      @get:Input
      abstract val find: Property<String>

      @get:InputFile
      @get:PathSensitive(PathSensitivity.ABSOLUTE)
      abstract val candidatesFile: RegularFileProperty

      @get:OutputFile
      abstract val resultsFile: RegularFileProperty

      @TaskAction
      fun search() {
          if (candidatesFile.get().getAsFile().readLines().contains(find.get())) {
              val msg = "Found a " + find.get() + "!"
              // ...
  ```

- **Do:**

  ```kotlin
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val candidatesFile: RegularFileProperty
  ```
