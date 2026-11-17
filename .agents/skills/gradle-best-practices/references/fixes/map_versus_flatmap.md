# Wire lazy task outputs using `map` and `flatMap`
`map_versus_flatmap`

**Rule:** Use `flatMap` to reach a `Provider`-typed output of a task and `map` to transform a value, keeping the dependency chain intact.

---

- **Fix:** `generatorTask.flatMap { it.outputFile }.map { it.asFile.readText() }`, with the producer's output declared as an annotated abstract getter (`@OutputFile abstract val outputFile: RegularFileProperty`).
- **Don't:**

  ```kotlin
  val generatorTask = tasks.register<GeneratorTask>("generator") {
      outputFile.set(layout.buildDirectory.file("eager-output.txt"))
  }

  tasks.register<ConsumerTask>("consumeEager") {
      inputFile.set(generatorTask.flatMap { it.outputFile })
      inputContent.set(generatorTask.map {
          it.outputFile.get().asFile.readText()
      })
  }
  ```

- **Do:**

  ```kotlin
  val generatorTask = tasks.register<GeneratorTask>("generator") {
      outputFile.set(layout.buildDirectory.file("output.txt"))
  }

  tasks.register<ConsumerTask>("consumeLazy") {
      inputFile.set(generatorTask.flatMap { it.outputFile })
      inputContent.set(
          generatorTask.flatMap { it.outputFile }
              .map { it.asFile.readText() }
      )
  }
  ```
