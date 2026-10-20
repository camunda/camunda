# Use Content Filtering with multiple Repositories
`use_content_filtering`

**Rule:** When several repositories are declared, filter which coordinates come from each, so resolution is predictable and a dependency cannot be served by an unintended repository.

---

- **Fix:**

  ```kotlin
  repositories {
      google { content { includeGroupByRegex("androidx.*"); includeGroup("com.google.gms") } }
      mavenCentral()
  }
  ```

  or `exclusiveContent { forRepository { google() }; filter { includeGroupByRegex("androidx.*") } }`.
- **Don't:**

  ```kotlin
  dependencyResolutionManagement {
      repositories {
          mavenCentral()
          google()
      }
  }
  ```

- **Do:**

  ```kotlin
  dependencyResolutionManagement {
      repositories {
          google {
              content {
                  // Use this repository for androidx and GMS dependencies
                  includeGroupByRegex("androidx.*")
                  includeGroup("com.google.gms")
              }
          }
          // Specify the fallback repository last
          mavenCentral()
      }
  }
  ```
