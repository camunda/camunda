# Set Build Flags in `gradle.properties`
`use_the_gradle_properties_file`

**Rule:** Set Gradle build flags in the root `gradle.properties`, checked into source control, rather than passing them per-invocation.

---

- **Fix:** Move the flags into the root `gradle.properties`, one `key=value` per line, and commit it.
- **Don't:**

  ```text
  ./gradlew build --continue --parallel
  ```

  Supplied per invocation: easily forgotten, and applied inconsistently across
  machines and CI.
- **Do:**

  ```properties
  # gradle.properties, in the root project, committed to source control
  org.gradle.continue=true
  org.gradle.parallel=true
  ```
