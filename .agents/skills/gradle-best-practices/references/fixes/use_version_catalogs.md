# Use Version Catalogs to Centralize Dependency Versions
`use_version_catalogs`

**Rule:** Centralize versions in `gradle/libs.versions.toml` rather than declaring them in build scripts or extension properties.

---

- **Fix:** Create `gradle/libs.versions.toml` with `[versions]` and `[libraries]` sections, and replace each declaration with its `libs.` accessor:

  ```toml
  [versions]
  groovy = "3.0.5"
  commons-lang3 = "3.17.0"
  [libraries]
  groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
  commons-lang3 = { module = "org.apache.commons:commons-lang3", version.ref = "commons-lang3" }
  ```

- **How to spell the accessor.** This is the one part that decides whether the fix
  works, so get it right and the rest is mechanical.
  Gradle turns each `-` (and `_`) in a library key into a **dot**, i.e. a nested
  accessor. It does *not* camel-case it. So the two entries above are referenced as:

  ```groovy
  api libs.groovy.core          // key groovy-core
  api libs.commons.lang3        // key commons-lang3
  ```

  `libs.commonsLang3` is not a thing, and produces
  `Could not get unknown property 'commonsLang3' for extension 'libs'`.
  Any camelCase inside a single key segment survives as-is: a key
  `jackson-dataformatCsv` is `libs.jackson.dataformatCsv`.
- **How to apply it safely.** Do the whole practice in one step — write the `.toml`,
  then update *every* reference site — and build immediately after. Every accessor has
  to agree with every key, so a single mismatch fails configuration for the whole build
  and the error names the accessor it could not resolve, which tells you exactly which
  key to look at. That makes it cheap to correct: fix the accessor and build again. This
  practice is worth attempting even on a build with many dependency declarations — it is
  high-value and the failure mode is both loud and easy to localize.
- **Don't:**

  ```kotlin
  plugins {
      id("java-library")
      id("com.github.ben-manes.versions").version("0.45.0")
  }
  val groovyVersion = "3.0.5"

  dependencies {
      api("org.codehaus.groovy:groovy:$groovyVersion")
      api("org.codehaus.groovy:groovy-json:$groovyVersion")
      api("org.codehaus.groovy:groovy-nio:$groovyVersion")

      testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")

      implementation("org.apache.commons:commons-lang3") {
          version {
          // ...
  ```

- **Do:**

  ```kotlin
  // build.gradle.kts
  plugins {
      id("java-library")
      alias(libs.plugins.versions)
  }
  dependencies {
      api(libs.bundles.groovy)
      testImplementation(libs.junit.jupiter)
      implementation(libs.commons.lang3)
  }
  ```

  ```toml
  # gradle/libs.versions.toml
  [versions]
  groovy = "3.0.5"
  junit-jupiter = "5.10.0"

  [libraries]
  groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
  groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
  groovy-nio = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }
  commons-lang3 = { group = "org.apache.commons", name = "commons-lang3", version = { strictly = "[3.8, 4.0[", prefer = "3.9" } }
  junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit-jupiter" }

  [bundles]
  groovy = ["groovy-core", "groovy-json", "groovy-nio"]

  [plugins]
  versions = { id = "com.github.ben-manes.versions", version = "0.45.0" }
  ```
