# Apply Exclusions Narrowly
`apply_exclusions_narrowly`

**Rule:** Attach an exclusion to the specific dependency that drags in the unwanted module, and name the module - not the whole group, and not the whole configuration.

---

- **Fix:** Move the exclusion onto the offending declaration and name both coordinates:

  ```kotlin
  implementation("org.hibernate:hibernate-core:3.6.10.Final") {
      exclude(group = "cglib", module = "cglib")
  }
  ```

- **Don't:**

  ```kotlin
  dependencies {
      implementation("org.apache.commons:commons-pool2:2.12.1")
      implementation("org.hibernate:hibernate-core:3.6.10.Final")
      // ... other dependencies ...
  }

  configurations {
      "implementation" {
          exclude(group = "cglib")
      }

      "implementation" {
          exclude(group = "org.ow2.asm", module = "asm-util")
      }
  }
  // ...
  ```

- **Do:**

  ```kotlin
  dependencies {
      implementation("org.apache.commons:commons-pool2:2.12.1") {
          exclude(group = "cglib", module = "cglib")
          exclude(group = "org.ow2.asm", module = "asm-util")

      }
      implementation("org.hibernate:hibernate-core:3.6.10.Final") {
          exclude(group = "cglib", module = "cglib")
          exclude(group = "javassist", module = "javassist")
      }
      // ... other dependencies ...
  }
  ```
