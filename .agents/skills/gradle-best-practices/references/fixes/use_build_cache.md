# Use the Build Cache
`use_build_cache`

**Rule:** Enable the build cache so task outputs are reused instead of recomputed when inputs have not changed.

---

- **Fix:** Add `org.gradle.caching=true` to the root `gradle.properties`.
- **Don't:**

  ```properties
  # caching is off by default
  # org.gradle.caching=false
  ```

- **Do:**

  ```properties
  org.gradle.caching=true
  ```
