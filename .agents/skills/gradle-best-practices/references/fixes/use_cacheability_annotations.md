# Favor `@CacheableTask` and `@DisableCachingByDefault` over `cacheIf(Spec)` and `doNotCacheIf(String, Spec)`
`use_cacheability_annotations`

**Rule:** Declare cacheability on the task class with `@CacheableTask` or `@DisableCachingByDefault`, not per instance with `cacheIf` / `doNotCacheIf`.

---

- **Fix:** Annotate the class — `@CacheableTask abstract class MyTask : DefaultTask()` — or `@DisableCachingByDefault(because = "...")` when it should not be cached, and remove the per-instance call.
- **Don't:**

  ```kotlin
  abstract class CalculatorTask : DefaultTask() { /* ... */ }

  tasks.register<CalculatorTask>("add1") {
      outputs.cacheIf { true }
  }
  tasks.register<CalculatorTask>("add2") {
      outputs.cacheIf { true }
  }
  ```

- **Do:**

  ```kotlin
  @CacheableTask
  abstract class CalculatorTask : DefaultTask() { /* ... */ }

  tasks.register<CalculatorTask>("add1")
  tasks.register<CalculatorTask>("add2")
  ```
