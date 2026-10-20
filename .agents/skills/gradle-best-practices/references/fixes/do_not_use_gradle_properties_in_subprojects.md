# Do not use `gradle.properties` in subprojects
`do_not_use_gradle_properties_in_subprojects`

**Rule:** Do not place a `gradle.properties` file inside a subproject to configure the build; properties there are handled inconsistently.

---

- **Fix:** Copy the values into the root `gradle.properties` — creating it if it does not exist — and then **delete the subproject's `gradle.properties` outright**. The fix is not complete until that file is gone: truncating it to zero bytes leaves the violation in place, because what is wrong is a `gradle.properties` existing at that path at all, not what it holds. For genuinely per-subproject configuration, use a convention plugin with an extension type.
- **Don't:**

  ```kotlin
  // build.gradle.kts
  // This file is located in /app
  tasks.register("printProperties") {
      val propA = project.findProperty("propertyA")
      val propB = project.findProperty("propertyB")

      doLast {
          println("propertyA in app: $propA")
          println("propertyB in app: $propB")
      }
  }
  ```

  ```kotlin
  // build.gradle.kts
  // This file is located in /util
  tasks.register("printProperties") {
      val propA = project.findProperty("propertyA")
      val propB = project.findProperty("propertyB")

      doLast {
          println("propertyA in util: $propA")
          println("propertyB in util: $propB")
      }
  }
  ```

- **Do:**

  ```kotlin
  // build.gradle.kts
  // This file is located in /app
  plugins {
      id("project-properties")
  }

  myProperties {
      propertyA = providers.gradleProperty("propertyA")
      propertyB = providers.gradleProperty("propertyB")
  }
  ```

  ```kotlin
  // build.gradle.kts
  // This file is located in /util
  plugins {
      id("project-properties")
  }

  myProperties {
      propertyA = providers.gradleProperty("propertyA")
      propertyB = "otherValue"
  }
  ```
