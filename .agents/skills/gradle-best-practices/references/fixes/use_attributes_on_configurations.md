# Always Declare Attributes on Consumable and Resolvable Configurations
`use_attributes_on_configurations`

**Rule:** Every custom consumable or resolvable configuration must declare at least one attribute, so variant-aware resolution can match it.

---

- **Fix:** Declare matching attributes on both ends and drop the explicit configuration name:

  ```kotlin
  configurations.consumable("customElements") {
      attributes {
          attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
      }
      outgoing { artifact(generateFile) }
  }
  ```

- **Don't:**

  ```kotlin
  // producer/build.gradle.kts
  configurations.consumable("customElements")

  val generateFile = tasks.register("generateFile") {
      val outputFile = layout.buildDirectory.file("custom/output.txt")
      outputs.file(outputFile)
      doLast {
          outputFile.get().asFile.writeText("Custom output from producer")
      }
  }

  artifacts {
      add("customElements", generateFile)
  }
  ```

  ```kotlin
  // consumer/build.gradle.kts
  val customElementsDependencies = configurations.dependencyScope("customElementsDependencies")

  dependencies {
      customElementsDependencies(project(path = ":producer", configuration = "customElements"))
  }

  val customElements = configurations.resolvable("customElements") {
      extendsFrom(customElementsDependencies.get())
  }

  tasks.register("resolveCustom") {
      inputs.files(customElements.get())
      doLast {
          inputs.files.forEach { file: File ->
              logger.lifecycle("Resolved: ${file.name}")
          }
      }
  }
  ```

- **Do:**

  ```kotlin
  // producer/build.gradle.kts
  val CUSTOM_ATTRIBUTE = Attribute.of("custom", String::class.java)
  dependencies.attributesSchema.attribute(CUSTOM_ATTRIBUTE)

  val generateFile = tasks.register("generateFile") {
      val outputFile = layout.buildDirectory.file("custom/output.txt")
      // ...
      }
  }

  configurations {
      consumable("customElements") {
          attributes {
              attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
              attribute(CUSTOM_ATTRIBUTE, "my-custom-value")
          }
          outgoing {
          // ...
  ```

  ```kotlin
  // consumer/build.gradle.kts
  val customElementsDependencies = configurations.dependencyScope("customElementsDependencies")

  dependencies {
      customElementsDependencies(project(":producer"))
  }

  val CUSTOM_ATTRIBUTE = Attribute.of("custom", String::class.java)
  dependencies.attributesSchema.attribute(CUSTOM_ATTRIBUTE)

  val customElements = configurations.resolvable("customElements") {
      extendsFrom(customElementsDependencies.get())
      attributes {
          attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
          attribute(CUSTOM_ATTRIBUTE, "my-custom-value")
      }
  }

  tasks.register("resolveCustom") {
      inputs.files(customElements.get())
      doLast {
          inputs.files.forEach { file: File ->
              logger.lifecycle("Resolved: ${file.name}")
              // ...
  ```
