# Favor collection property types over a `Property` holding a collection
`favor_collection_properties`

**Rule:** A `Property` treats its value as one opaque unit, so a collection inside it can only be replaced wholesale. `ListProperty<T>`, `SetProperty<T>` and `MapProperty<K, V>` lift the collection into the property, which makes contributions additive, lets individual elements be lazy providers that carry their own task dependencies, and removes the need to read eagerly during configuration.

---

- **Fix:** Declare the input as `ListProperty` / `SetProperty` / `MapProperty` and contribute with `add(T)`, `addAll(Iterable<T>)` or `put(K, V)`. Gradle rejects `Property<List<T>>` outright, but `Property<Collection<T>>`, `Property<Iterable<T>>` and plain `List<T>` fields are accepted and must be avoided by hand.
- **Don't:**

  ```kotlin
  abstract class Report : DefaultTask() {
      @get:Input
      abstract val messages: Property<Collection<String>>
  }

  val report = tasks.register<Report>("report") {
      messages = listOf("Project: ${project.name}")
  }

  report.configure {
      messages = messages.get() + "Built with Gradle"        // eager read to append
      messages = writeVersion.flatMap { it.versionFile }     // and this overwrites the line above
          .map { listOf("Version: ${it.asFile.readText()}") }
  }
  ```

- **Do:**

  ```kotlin
  abstract class Report : DefaultTask() {
      @get:Input
      abstract val messages: ListProperty<String>
  }

  val report = tasks.register<Report>("report") {
      messages.add("Project: ${project.name}")
  }

  report.configure {
      messages.add("Built with Gradle")
      messages.add(writeVersion.flatMap { it.versionFile }
          .map { "Version: ${it.asFile.readText()}" })       // dependency carried automatically
  }
  ```
