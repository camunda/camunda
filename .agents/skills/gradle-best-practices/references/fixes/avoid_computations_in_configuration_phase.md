# Avoid Expensive Computations in Configuration Phase
`avoid_computations_in_configuration_phase`

**Rule:** Move file I/O, network calls and CPU-heavy work out of the configuration phase and into task actions, so it runs only when needed.

---

- **Fix:** Move the logic into a task's `@TaskAction` (or a `Provider` that is only queried at execution time), and declare its inputs and outputs.
- **Don't:**

  ```kotlin
  abstract class MyTask : DefaultTask() {
      @get:Input
      lateinit var computationResult: String
      @TaskAction
      fun run() {
          logger.lifecycle(computationResult)
      }
  }

  fun heavyWork(): String {
      println("Start heavy work")
      Thread.sleep(5000)
      println("Finish heavy work")
      return "Heavy computation result"
  }
  // ...
  ```

- **Do:**

  ```kotlin
  abstract class MyTask : DefaultTask() {
      @TaskAction
      fun run() {
          logger.lifecycle(heavyWork())
      }

      fun heavyWork(): String {
          logger.lifecycle("Start heavy work")
          Thread.sleep(5000)
          logger.lifecycle("Finish heavy work")
          return "Heavy computation result"
      }
  }

  tasks.register<MyTask>("myTask")
  ```
