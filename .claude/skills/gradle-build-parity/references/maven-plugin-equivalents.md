# Maven Plugin → Gradle Equivalents

Recipes for replicating Maven build-time behaviors (code generation, resource templating,
version pinning) in the Gradle build. All are **config-cache-safe** — keep them that way.

Maven stays the source of truth: values (versions, spec paths, template tokens) come from the
relevant `pom.xml`, not hardcoded design choices.

## Config-cache-safe reads of `pom.xml`

`settings.gradle.kts` and `buildSrc/build.gradle.kts` parse `parent/pom.xml` (and a few
module poms) to source catalog versions. **How the file is read matters for correctness, not
just perf:**

```kotlin
// CORRECT — tracked by the configuration cache; invalidates on pom content change
val pomVersions: Map<String, String> =
  parsePomProperties(
    providers.fileContents(layout.rootDirectory.file("parent/pom.xml")).asText.get()
  )
```

- `DocumentBuilderFactory.parse(file(...))` — raw `File` read, **NOT tracked**. Pom edits do
  not invalidate the cache → stale versions silently reused. Wrong.
- `ValueSource` with a `RegularFileProperty` parameter — also **NOT** content-tracked.
- Only `providers.fileContents(...).asText` is tracked. Parse from the resulting `String`
  (`parsePomProperties(xml: String)`), never from a `File`.

Live in `settings.gradle.kts` (`pomVersion`, `tasklistQaPomVersion`, `optimizePomVersion`).

## `templating-maven-plugin` → `Sync` + `ReplaceTokens`

Maven's `templating-maven-plugin` generates sources (e.g. `Version.java`,
`PreviousVersion.java`) from `src/main/java-templates/` by substituting `${...}` tokens. Gradle
equivalent — a `Sync` task with an Ant `ReplaceTokens` filter:

```kotlin
val generatePreviousVersionJava by
  tasks.registering(Sync::class) {
    from("src/main/java-templates")
    into(layout.buildDirectory.dir("generated/sources/java-templates/java/main"))
    inputs.property("projectPreviousVersion", "8.8.0")        // value from optimize/pom.xml
    val tokenMap = mapOf("project.previousVersion" to "8.8.0")
    filter<org.apache.tools.ant.filters.ReplaceTokens>(
      "beginToken" to "\${",
      "endToken" to "}",
      "tokens" to tokenMap,
    )
  }

sourceSets { main { java { srcDir(generatePreviousVersionJava) } } }
```

**Config-cache gotcha:** do NOT reference `project.version` (or any live `project` accessor)
inside the `filter { }` lambda — it captures a script reference the cache can't serialize.
Resolve the value into a plain `String`/`Map` first, then pass it to `ReplaceTokens`. Register
the value with `inputs.property(...)` so token changes invalidate the task.

Wiring the task object directly into `srcDir(...)` carries the task dependency automatically —
no explicit `dependsOn` needed. Live in `optimize/upgrade`, `optimize/util/optimize-commons`,
and ~11 other modules.

## `openapi-generator-maven-plugin` → `GenerateTask` (multiple specs per module)

The `org.openapi.generator` Gradle plugin registers a single `openApiGenerate` task. When a
Maven module has **multiple** `openapi-generator-maven-plugin` executions (different spec files
or type mappings), register each extra one manually as a `GenerateTask`:

```kotlin
val openApiGenerateSimple by
  tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("spring")
    inputSpec.set("$openapiDir/rest-api.yaml")
    outputDir.set("${project.layout.buildDirectory.get()}/generated/openapi-simple")
    modelPackage.set("io.camunda.gateway.protocol.model.simple")

    // declare inputs explicitly so up-to-date checks work
    inputs.files(fileTree(openapiDir) { include("**/*.yaml", "**/*.yml") })
      .withPropertyName("openapiSpecs")
      .withPathSensitivity(PathSensitivity.RELATIVE)

    // models only, no apis / supporting files
    globalProperties.set(mapOf("models" to "", "apis" to "false", "supportingFiles" to "false"))

    // map schema types Gradle would otherwise generate as separate classes onto plain types.
    // e.g. `type: string` key schemas → String. Use typeMappings (NOT importMappings).
    typeMappings.set(mapOf(
      "ElementInstanceKey" to "String",
      "ProcessInstanceKeyFilterProperty" to "String",
      // ... full list mirrors the Maven <typeMappings> for that execution
    ))

    skipValidateSpec.set(true)
    templateDir.set("${project.projectDir}/src/main/resources/templates/java-spring/simple")
    configOptions.set(mapOf(
      "serializationLibrary" to "jackson",
      "library" to "spring-boot",
      "jdk8" to "true",
      "openApiNullable" to "false",
      "additionalModelTypeAnnotations" to "...;@org.jspecify.annotations.NullMarked",
    ))
  }

sourceSets { main { java { srcDir(layout.buildDirectory.dir("generated/openapi-simple/src/main/java")) } } }
tasks.named("compileJava") { dependsOn(openApiGenerateSimple) }
```

Notes:
- **`typeMappings`, not `importMappings`**, is the mechanism used to collapse key/filter
  schemas onto `String`/`Integer`/enum types — mirror the Maven `<typeMappings>` exactly.
- `globalProperties` MUST include `"models" to ""` — without the `models` key, **zero** model
  files are generated (empty string = "all models").
- Match `configOptions` and `templateDir` to the Maven execution's `<configOptions>` and
  `<templateDirectory>`; template differences cause subtly different generated code.
- Live: `gateways/gateway-model` (two generations), `dist` (backups/cluster/exporter),
  `clients/java`.

## Per-module Spring / Spring Boot version pinning

Most modules resolve Spring Boot from the catalog platform (current major). A module that must
build against an **older** Spring Boot line (e.g. the SB3 starter / testing modules) pins via
`resolutionStrategy.eachDependency` — centralized in `buildlogic.spring-boot-3-conventions`:

```kotlin
configurations.all {
  exclude(group = "org.springframework.boot", module = "spring-boot-health")  // no SB3 equivalent
  resolutionStrategy.eachDependency {
    when (requested.group) {
      "org.springframework.boot" -> { useVersion("3.5.14"); because("Spring Boot 3.x compatibility module") }
      "org.springframework"      -> { useVersion("6.2.18"); because("Spring 6.x required for Spring Boot 3.x") }
    }
  }
}
```

Apply alongside the module's existing convention:

```kotlin
plugins {
  id("buildlogic.server-conventions")        // or client-conventions
  id("buildlogic.spring-boot-3-conventions")
}
```

**Do NOT** add a second `enforcedPlatform("...:3.5.14")` — it collides with the platform the
server/client convention already adds (two `strictly` constraints → FAILED resolution).
`eachDependency` overrides versions without a second platform.

`exclude` the modules that exist only in the newer Spring Boot line (e.g. `spring-boot-health`
in SB4) — the older starter pulls them transitively and they won't resolve.

## SBE codegen — keep the SBE tool off the runtime classpath

`buildlogic.sbe-conventions` generates Java from SBE schemas by running `SbeTool`. The SBE tool
drags a newer Agrona than the module runtime wants; forcing it onto the runtime classpath broke
`zeebe-scheduler` tests. Isolate it in a dedicated resolvable configuration used **only** by the
generator task:

```kotlin
val sbeTool by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}
dependencies { sbeTool("uk.co.real-logic:sbe-tool:$sbeToolVersion") }  // version from catalog

tasks.register<JavaExec>("generateSbe") {
  mainClass.set("uk.co.real_logic.sbe.SbeTool")
  classpath = configurations.getByName("sbeTool")   // NOT runtimeClasspath
  // ... jvmArgs --add-opens java.base/jdk.internal.misc, systemProperties, inputs/outputs
}
```

Same shape applies to any codegen tool whose deps must not leak into the module's runtime.
