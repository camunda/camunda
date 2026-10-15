# Maven Plugin → Gradle Equivalents

Short recipes for preserving Maven build-time behavior in Gradle. Maven remains the source of
truth: versions, paths, and template values must come from the relevant POM or the generated
`libs` catalog.

These are patterns, not copies of complete module build files. Prefer the current implementation
linked in each section when adding or changing a module.

## POM values and configuration-cache tracking

Use Gradle's file-content provider when parsing a POM during configuration. Reading a POM through
`File.readText()` or `DocumentBuilderFactory.parse(file)` does not register the file contents as a
configuration-cache input.

The settings build uses the settings resolver plugin:

```kotlin
plugins {
  id("io.camunda.gradle.settings-pom-resolver")
}

val settingsPomResolver = extensions.getByType<SettingsPomResolver>()
val pomVersions = settingsPomResolver.propertiesForPom("parent/pom.xml")
```

Build logic uses the same tracked input pattern directly:

```kotlin
val pomVersions =
  PomResolver(
      providers.fileContents(layout.projectDirectory.file("../parent/pom.xml")).asText.get()
    )
    .properties()
```

Parse the resulting `String`; do not pass a raw `File` to the XML parser. The settings resolver
implementation is in `gradle/build-logic/src/main/kotlin/io/camunda/gradle/pom/SettingsPomResolverPlugin.kt`.

## `templating-maven-plugin` → `Sync` + `ReplaceTokens`

For Maven template filtering, resolve the value before configuring the filter, register it as a
task input, and attach the `Sync` task's output directory to the source set:

```kotlin
val projectVersion = project.version.toString()
val generateVersionJava =
  tasks.register<Sync>("generateVersionJava") {
    from("src/main/java-templates")
    into(layout.buildDirectory.dir("generated/sources/java-templates/java/main"))
    inputs.property("projectVersion", projectVersion)

    filter<org.apache.tools.ant.filters.ReplaceTokens>(
      "beginToken" to "\${",
      "endToken" to "}",
      "tokens" to mapOf("project.version" to projectVersion),
    )
  }

sourceSets { main { java { srcDir(generateVersionJava) } } }
```

Do not read `project` state inside the filter action. Use a plain `String` or `Map` and register
it as an input so changing the token invalidates the task. Wiring the task into `srcDir` carries
the task dependency automatically.

Current example: `optimize/util/optimize-commons/build.gradle.kts`. Do not add this task to
`optimize/upgrade`; that module has no Maven template source.

## `openapi-generator-maven-plugin` → additional `GenerateTask`

The Gradle OpenAPI plugin provides one `openApiGenerate` task. For each additional Maven plugin
execution, register a separate `GenerateTask` and wire its generated source directory into the
main source set:

```kotlin
val openApiGenerateSimple =
  tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>(
    "openApiGenerateSimple"
  ) {
    generatorName.set("spring")
    inputSpec.set("$openapiDir/rest-api.yaml")
    outputDir.set("${project.layout.buildDirectory.get()}/generated/openapi-simple")
    modelPackage.set("io.camunda.gateway.protocol.model.simple")

    globalProperties.set(
      mapOf("models" to "", "apis" to "false", "supportingFiles" to "false")
    )
    typeMappings.set(
      mapOf(
        "ElementInstanceKey" to "String",
        "ProcessInstanceKeyFilterProperty" to "String",
      )
    )
    skipValidateSpec.set(true)
    templateDir.set("${project.projectDir}/src/main/resources/templates/java-spring/simple")
  }

sourceSets {
  main { java { srcDir(layout.buildDirectory.dir("generated/openapi-simple/src/main/java")) } }
}
tasks.named("compileJava") { dependsOn(openApiGenerateSimple) }
```

The real execution must mirror the Maven execution's `typeMappings`, `configOptions`, template
directory, and input files. Use `typeMappings`, not `importMappings`, when collapsing schemas onto
existing types. Keep the empty `models` entry in `globalProperties`; without it, no model files
are generated.

Current example: `gateways/gateway-model/build.gradle.kts`.

## Spring Boot version pinning

The `buildlogic.spring-boot-3-conventions` plugin owns the Maven POM lookup, the exclusion for
newer-only modules, and `resolutionStrategy.eachDependency`. Apply the convention; do not copy its
implementation into individual modules:

```kotlin
plugins {
  id("buildlogic.client-conventions") // or server-conventions for server modules
  id("buildlogic.spring-boot-3-conventions")
}
```

The convention resolves `version.spring-boot` and `version.spring` from the module POM through a
tracked `PomResolver`. Do not add a second Spring Boot platform: competing strict constraints can
make dependency resolution fail. Current consumers are:

- `clients/camunda-spring-boot-3-starter/build.gradle.kts`
- `testing/camunda-process-test-spring-boot-3/build.gradle.kts`

## SBE code generation

`buildlogic.sbe-conventions` owns the dedicated `sbeTool` configuration, the `generateSbe` task,
generated source wiring, and generated resource packaging. Modules should only provide their
schemas and task arguments:

```kotlin
plugins { id("buildlogic.sbe-conventions") }

sbe {
  inputFiles.from(
    layout.projectDirectory.file("src/main/resources/protocol.xml"),
    layout.projectDirectory.file("src/main/resources/common-types.xml"),
  )
}

tasks.named<JavaExec>("generateSbe") {
  args("src/main/resources/protocol.xml")
}
```

Do not recreate `sbeTool` or `generateSbe`, and do not put the SBE tool on a runtime or compile
classpath. Its newer Agrona dependency must remain isolated from the module runtime.

Implementation: `buildSrc/src/main/kotlin/buildlogic.sbe-conventions.gradle.kts`.
