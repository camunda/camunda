# Obtain Loggers via `Logging.getLogger(Class)` outside of Tasks
`obtain_loggers_via_logging_get_logger`

**Rule:** Name a logger after the class that owns it. A logger reached through `Project` or `Script` is named after that Gradle type and shared by everything that reaches it that way, so output cannot be traced back to the code that produced it. `Task` is the exception: `Task.getLogger()` is shared too, but Gradle wraps it in per-task build-operation context, so console output is attributed automatically.

---

- **Fix:** Declare the logger in a companion object: `private val logger = Logging.getLogger(TheClass::class.java)`. Do not constructor-inject a `Logger` — it is not an injectable service, and `BuildServiceParameters` cannot carry one because the concrete implementation is not serializable for the configuration cache.
- **Don't:**

  ```kotlin
  abstract class GreetingService : BuildService<BuildServiceParameters.None> {
      @get:Inject
      abstract val logger: Logger          // not an injectable service

      fun greet(name: String) = logger.lifecycle("Hello, {}!", name)
  }

  abstract class GreetingPlugin : Plugin<Project> {
      override fun apply(project: Project) {
          project.logger.lifecycle("Applying GreetingPlugin")   // named org.gradle.api.Project
          // ...
      }
  }

  abstract class PluginGreetingTask : DefaultTask() {
      @TaskAction
      fun run() {
          project.logger.lifecycle("...")  // also breaks the configuration cache
      }
  }
  ```

- **Do:**

  ```kotlin
  abstract class GreetingService : BuildService<BuildServiceParameters.None> {
      companion object {
          private val logger = Logging.getLogger(GreetingService::class.java)
      }

      fun greet(name: String) = logger.lifecycle("Hello, {}!", name)
  }

  abstract class GreetingPlugin : Plugin<Project> {
      companion object {
          private val logger = Logging.getLogger(GreetingPlugin::class.java)
      }

      override fun apply(project: Project) {
          logger.lifecycle("Applying GreetingPlugin")
          // ...
      }
  }

  abstract class PluginGreetingTask : DefaultTask() {
      @TaskAction
      fun run() {
          logger.lifecycle("Greeting: {}", greeting.get())   // Task's own logger is fine
      }
  }
  ```

Three further reasons not to reach for the `Project` logger: `project.logger` during task execution is incompatible with the configuration cache; a `Plugin` constructor has no `Project` at all, since it arrives only via `apply(Project)`; and a `BuildService` never has one.
