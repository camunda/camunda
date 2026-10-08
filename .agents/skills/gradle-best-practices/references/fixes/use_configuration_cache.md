# Use the Configuration Cache
`use_configuration_cache`

**Rule:** Enable the configuration cache so the configuration phase is skipped and the task graph is loaded from disk.

---

- **Fix:** Add `org.gradle.configuration-cache=true` to the root `gradle.properties`. Enabling it can surface real incompatibilities — run the build afterwards and, if it fails, report the incompatibility rather than silently reverting the flag. Common blockers are the `afterEvaluate`, `Project`-in-task-action, and eager-configuration-resolution findings in `general.md` / `tasks.md`.
- **Don't:**

  ```properties
  # caching is off by default
  # org.gradle.configuration-cache=false
  ```

- **Do:**

  ```properties
  org.gradle.configuration-cache=true
  ```
