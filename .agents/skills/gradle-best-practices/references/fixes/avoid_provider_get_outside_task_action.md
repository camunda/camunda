# Do not call `get()` on a Provider outside a Task action
`avoid_provider_get_outside_task_action`

**Rule:** Do not query a provider during configuration; transform it with `map` / `flatMap` so the value is read at execution time.

---

- **Fix:** `currentEnvironment.map { "currentEnvironment=$it" }`; `layout.buildDirectory.file("out/report.txt")` instead of `layout.buildDirectory.get().asFile`.

  **Deleting the `.get()` is half the change.** Check the type of the parameter now receiving the provider, because the failure mode when you get this wrong is silent — it compiles, `./gradlew build` succeeds, and the wrong value lands on disk.

  | Receiving parameter | Passing a raw provider |
  |---|---|
  | `Provider<T>` / `Property<T>` — `.set(…)`, `Directory.file(Provider<CharSequence>)`, `ConfigurableFileCollection.from(…)` | correct and lazy |
  | `Object` / `String` / `Any` — `systemProperty(k, v)`, `environment(…)`, `args(…)`, `extra[…]`, string templates `"$p"` | **stringified.** Gradle calls `toString()` at execution and the value becomes the literal text `extension 'carLog' property 'logFileName'` |

  For the second row, resolve inside the task action — which is precisely where this practice allows `.get()`:

  ```kotlin
  tasks.register<JavaExec>("recordDemoMaintenance") {
      doFirst { systemProperty("carlog.file", carLogExtension.logFileName.get()) }
  }
  ```

  Renaming the variable to `resolvedX` does not resolve it. After removing a `.get()`, confirm the value's type at every use site, not just at the declaration.
- **Don't:**

  ```kotlin
  tasks.register<MyTask>("avoidThis") {
      myInput = "currentEnvironment=${currentEnvironment.get()}"
      myOutput = layout.buildDirectory.get().asFile.resolve("output-avoid.txt")
  }
  ```

- **Do:**

  ```kotlin
  tasks.register<MyTask>("doThis") {
      myInput = currentEnvironment.map { "currentEnvironment=$it" }
      myOutput = layout.buildDirectory.file("output-do.txt")
  }
  ```
