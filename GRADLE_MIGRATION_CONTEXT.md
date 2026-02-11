# Gradle Migration Context

## Goal

Run Gradle in parallel with Maven for faster builds on the Camunda monorepo (~120 subprojects). Maven (`parent/pom.xml`) is the **authoritative source** for dependency versions and dependencies. The goal is to get `./gradlew build -x test` to pass by fixing compilation errors — primarily missing dependencies and broken code generation that exist in the Gradle build but work fine in Maven.

## Instructions

- Maven (`parent/pom.xml`) is the **authoritative source** for dependency versions and dependencies
- **Always check the pom.xml files from Maven when something does not build in Gradle** — compare Maven deps against Gradle deps to find what's missing
- The `cs/gradle` branch contains all Gradle build files
- A `sync-maven-to-gradle-versions.py` script exists at the project root to sync Maven → Gradle versions
- Entries with `-x1` suffixes in the TOML represented intentional dual-version setups — Spring/Spring Boot x1 entries have been removed; other x1 entries (JUnit, testcontainers, jakarta-servlet) still exist

## Discoveries

- Code generation scripts in maven are sometimes located in parent/pom.xml and then only "enabled" in subprojects.
- **Sync script shadowing bug**: The TOML had both non-x1 entries (e.g., `spring-boot = "3.5.10"`) and x1 entries (e.g., `spring-boot-x1 = "4.0.0"`) pointing to the same Maven coordinates. The x1 entries overwrote the non-x1 entries in the script's `module_to_version_key` map, then got skipped by the `-x1` filter — so neither version was ever updated. Fixed by removing x1 entries first, then running sync.
- **Spring Boot 4.x introduced new modules**: `spring-boot-health`, `spring-boot-security`, `spring-boot-freemarker`, `spring-boot-webmvc`, `spring-boot-jackson2`, `spring-boot-hibernate`, `spring-boot-jdbc`, `spring-boot-webmvc-test`, `spring-boot-webtestclient` are new artifacts in Spring Boot 4.x that don't exist in 3.x. These needed to be added to both the TOML catalog and individual build files.
- **OpenAPI convention plugin was not generating models**: The `buildlogic.openapi-spring-conventions.gradle.kts` had `globalProperties` set to only `"apis" to "false"` but was missing `"models" to ""`. Without `"models"` key, zero model files were generated. The working `buildlogic.openapi-java-client-conventions.gradle.kts` had it correct.
- **OpenAPI `useSpringBoot3` config option**: The Maven parent POM sets `<useSpringBoot3>true</useSpringBoot3>` and `<sourceFolder>src/main</sourceFolder>` in the openapi-generator-maven-plugin config. This produces cleaner templates with Jakarta imports. The Gradle plugin needed `"useSpringBoot3" to "true"` added to `configOptions`.
- **OpenAPI Gradle vs Maven template difference**: Even with identical versions (7.18.0) and same config, the Gradle openapi-generator plugin generates an `import io.camunda.gateway.protocol.model.ElementInstanceKey` in the `ProcessInstanceModificationActivateInstructionAncestorElementInstanceKey` interface file, while Maven doesn't. The `ElementInstanceKey` class is never generated (it's a `type: string` schema). Fixed using `importMappings` to map `ElementInstanceKey` to `java.lang.String`.
- **Maven's `templating-maven-plugin`** is used by optimize modules to generate `Version.java` and `PreviousVersion.java` from `src/main/java-templates/` directories. These need equivalent Gradle tasks using `Sync` + `ReplaceTokens` filter.
- **Config cache compatibility**: Using `project.version` in a `filter { }` lambda captures a Gradle script reference. Must use `org.apache.tools.ant.filters.ReplaceTokens` instead.
- **`dependencyResolutionManagement` with `PREFER_SETTINGS`**: Centralized repos work; `buildSrc/build.gradle.kts` still uses its own `gradlePluginPortal()` without conflict.
- **Content filter gotcha**: `org.camunda.bpm.extension.dmn.scala:dmn-engine` is only on Maven Central (Artifactory returns 401/409), so Maven Central has NO content exclusions.
- **dist module needs 3 OpenAPI generation tasks**: The `dist/pom.xml` has 3 `openapi-generator-maven-plugin` executions (`backups`, `cluster`, `exporter`) that each generate models from different spec files. Since the Gradle `org.openapi.generator` plugin only registers a single `openApiGenerate` task, additional tasks must be registered manually using `GenerateTask` class.

## Accomplished

### Completed (prior sessions)
1. Removed init script (`gradle/init.d/fix-jakarta-json.gradle`) — eliminated `eachDependency` callback
2. Centralized repositories in `settings.gradle.kts` with content filters
3. Made `mavenLocal()` conditional (`-PuseMavenLocal`)
4. Removed repository block from convention plugin
5. Fixed pre-existing build errors in `dist/build.gradle.kts` and `zeebe/restore/build.gradle.kts`
6. Phase 1-3 of `api()` → `implementation()` migration (leaf projects, SLF4J, safe third-party deps)
7. Replaced all Spring/Spring Boot `.x1` references in 32 build files with non-x1 equivalents

### Completed (recent sessions)
8. **Removed all Spring/Spring Boot x1 entries** from `gradle/libs.versions.toml` (10 version entries + 10 library entries)
9. **Ran sync script** — updated 10 versions: spring-boot core `3.5.10→4.0.2`, spring-framework `6.2.15→7.0.3`, spring-boot-test `4.0.0→4.0.2`
10. **Added new Spring Boot 4.x modules to TOML**: `spring-boot-health`, `spring-boot-security`, `spring-boot-freemarker`, `spring-boot-webmvc`, `spring-boot-jackson2`, `spring-boot-hibernate`, `spring-boot-jdbc`, `spring-boot-webmvc-test`, `spring-boot-webtestclient` (versions + libraries all at 4.0.2)
11. **Fixed missing deps in build files**:
    - `zeebe/util/build.gradle.kts` — added `spring-boot-health`
    - `dist/build.gradle.kts` — added `spring-boot-health`
    - `zeebe/exporter-filter/build.gradle.kts` — added `jackson-databind`, `jackson-core`
    - `gateways/gateway-model/build.gradle.kts` — added `spring-web`
    - `optimize/util/optimize-commons/build.gradle.kts` — added `log4j-core` + Version.java template generation task
    - `authentication/build.gradle.kts` — added `spring-boot-security`, `camunda-gateway-model` project dep
    - `optimize/backend/build.gradle.kts` — added `spring-boot-freemarker`
    - `zeebe/gateway-rest/build.gradle.kts` — added `spring-boot-webmvc`, `spring-boot-jackson2`
    - `configuration/build.gradle.kts` — added `dynamic-node-id-provider` project dep
12. **Fixed OpenAPI spring convention plugin** — added `"models" to ""`, `"supportingFiles" to "false"` to `globalProperties`, added `"useSpringBoot3" to "true"` to `configOptions`, added `importMappings` for `ElementInstanceKey` → `java.lang.String`
13. **Added second OpenAPI generation** (`simple` execution) to `gateways/gateway-model/build.gradle.kts` with all type mappings from Maven pom.xml, `"useSpringBoot3" to "true"`, and `importMappings` for `ElementInstanceKey`
14. **Added `PreviousVersion.java` template generation** to `optimize/upgrade/build.gradle.kts` using `Sync` + `ReplaceTokens` with value `8.8.0` from `optimize/pom.xml`'s `<project.previousVersion>` property
15. **Added `ElementInstanceKey` to `String` type mapping** in both convention plugin and gateway-model simple task (though the `importMappings` approach was what actually fixed the compilation)
16. **Added 3 OpenAPI generation tasks to `dist/build.gradle.kts`**: `openApiGenerateBackups` (backup-management-api.yaml → `io.camunda.management.backups`), `openApiGenerateCluster` (cluster-api.yaml → `io.camunda.zeebe.management.cluster`), `openApiGenerateExporter` (exporter-api.yaml → `io.camunda.zeebe.management.cluster`)

### In Progress — Where We Left Off
- **`:camunda-zeebe:compileJava` was building** when the session was interrupted. The 3 OpenAPI generation tasks were added to `dist/build.gradle.kts` but the build was not yet verified. Need to run `./gradlew :camunda-zeebe:compileJava` (or full `./gradlew build -x test`) to confirm it passes and find the next errors.

### Not Yet Done
- Verify `dist` OpenAPI generation compiles correctly
- Fix any remaining compilation errors that surface from `./gradlew build -x test`
- Other `-x1` duplicates still exist in TOML: `jakarta-servlet-x1`, `junit-jupiter-*-x1`, `junit-platform-commons-x1`, `junit-vintage-x1`, `testcontainers-junit-jupiter-x1`
- Phase 4 of api→implementation migration (remaining ~1,164 `api()` declarations)

## Relevant files / directories

### Modified in recent sessions
- `/home/carlosana/workspace/camunda/camunda/gradle/libs.versions.toml` — Removed Spring x1 entries, added spring-boot-health/security/freemarker/webmvc/jackson2/hibernate/jdbc/webmvc-test/webtestclient, versions synced to 4.0.2/7.0.3
- `/home/carlosana/workspace/camunda/camunda/buildSrc/src/main/kotlin/buildlogic.openapi-spring-conventions.gradle.kts` — Added `"models" to ""` to globalProperties, `"useSpringBoot3" to "true"` to configOptions, `importMappings` for ElementInstanceKey, `"ElementInstanceKey" to "String"` type mapping
- `/home/carlosana/workspace/camunda/camunda/gateways/gateway-model/build.gradle.kts` — Added `simple` OpenAPI generation task with full type mappings from Maven, `"useSpringBoot3" to "true"`, `importMappings`, `"ElementInstanceKey" to "String"` type mapping, spring-web dep
- `/home/carlosana/workspace/camunda/camunda/zeebe/util/build.gradle.kts` — Added spring-boot-health
- `/home/carlosana/workspace/camunda/camunda/dist/build.gradle.kts` — Added spring-boot-health, 3 OpenAPI generation tasks (backups, cluster, exporter) with `org.openapi.generator` plugin
- `/home/carlosana/workspace/camunda/camunda/zeebe/exporter-filter/build.gradle.kts` — Added jackson-databind, jackson-core
- `/home/carlosana/workspace/camunda/camunda/optimize/util/optimize-commons/build.gradle.kts` — Added log4j-core + Version.java template gen (Sync + ReplaceTokens)
- `/home/carlosana/workspace/camunda/camunda/authentication/build.gradle.kts` — Added spring-boot-security, camunda-gateway-model project dep
- `/home/carlosana/workspace/camunda/camunda/optimize/backend/build.gradle.kts` — Added spring-boot-freemarker
- `/home/carlosana/workspace/camunda/camunda/optimize/upgrade/build.gradle.kts` — Added PreviousVersion.java template gen task with value `8.8.0`
- `/home/carlosana/workspace/camunda/camunda/zeebe/gateway-rest/build.gradle.kts` — Added spring-boot-webmvc, spring-boot-jackson2
- `/home/carlosana/workspace/camunda/camunda/configuration/build.gradle.kts` — Added dynamic-node-id-provider project dep

### Key reference files (Maven POMs to consult for dep mismatches)
- `/home/carlosana/workspace/camunda/camunda/parent/pom.xml` — Maven parent POM (Spring 7.0.3, Spring Boot 4.0.2, has `<useSpringBoot3>true</useSpringBoot3>` and `<sourceFolder>src/main</sourceFolder>` in openapi-generator config)
- `/home/carlosana/workspace/camunda/camunda/bom/pom.xml` — Maven BOM POM
- `/home/carlosana/workspace/camunda/camunda/optimize/pom.xml` — Has `<project.previousVersion>8.8.0</project.previousVersion>`
- `/home/carlosana/workspace/camunda/camunda/gateways/gateway-model/pom.xml` — OpenAPI generator config (advanced + simple executions)
- `/home/carlosana/workspace/camunda/camunda/optimize/upgrade/pom.xml` — Uses templating-maven-plugin for PreviousVersion.java
- `/home/carlosana/workspace/camunda/camunda/dist/pom.xml` — 3 OpenAPI generator executions (backups, cluster, exporter), git-commit-id plugin, log4j annotation processor

### Convention plugins
- `/home/carlosana/workspace/camunda/camunda/buildSrc/src/main/kotlin/buildlogic.openapi-spring-conventions.gradle.kts` — OpenAPI spring model generation (modified)
- `/home/carlosana/workspace/camunda/camunda/buildSrc/src/main/kotlin/buildlogic.openapi-java-client-conventions.gradle.kts` — Working reference for OpenAPI client generation
- `/home/carlosana/workspace/camunda/camunda/buildSrc/src/main/kotlin/buildlogic.java-conventions.gradle.kts` — Base Java conventions

### Other relevant files
- `/home/carlosana/workspace/camunda/camunda/settings.gradle.kts` — Centralized repos + content filters
- `/home/carlosana/workspace/camunda/camunda/sync-maven-to-gradle-versions.py` — Version sync script
- `/home/carlosana/workspace/camunda/camunda/gradle.properties` — Has `org.gradle.configuration-cache=true`
- Branch: `cs/gradle`

## Workflow for fixing compilation errors

1. Run `./gradlew build -x test` to find the next failing task
2. Look at the error messages — typically `package X does not exist` or `cannot find symbol`
3. Check the Maven `pom.xml` for the failing module to find which dependency provides the missing package
4. Cross-reference with `settings.gradle.kts` to find the Gradle project name (Maven artifactIds map to Gradle project names via `project(":name").projectDir = file("path")` in settings)
5. Add the missing dependency to the Gradle `build.gradle.kts` file
6. If the missing package comes from code generation (OpenAPI, protobuf, templates), add the appropriate generation task
7. If a new library is needed, add version + library entries to `gradle/libs.versions.toml`
8. Re-run build to verify the fix and find the next error

## Key patterns

### Adding a new Spring Boot 4.x module to TOML
```toml
# In [versions] section (alphabetical order):
org-springframework-boot-spring-boot-MODULENAME = "4.0.2"

# In [libraries] section (alphabetical order):
org-springframework-boot-spring-boot-MODULENAME = { module = "org.springframework.boot:spring-boot-MODULENAME", version.ref = "org-springframework-boot-spring-boot-MODULENAME" }
```

### Referencing in build.gradle.kts
```kotlin
api(libs.org.springframework.boot.spring.boot.MODULENAME)
// dots in TOML key become dots in accessor
```

### Adding OpenAPI generation (multiple executions)
```kotlin
val openApiGenerateXxx by tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("spring")
    inputSpec.set("${projectDir}/src/main/resources/api/xxx-api.yaml")
    outputDir.set("${project.layout.buildDirectory.get()}/generated/openapi-xxx")
    modelPackage.set("com.example.xxx")
    globalProperties.set(mapOf("models" to "", "apis" to "false", "supportingFiles" to "false"))
    configOptions.set(mapOf("useSpringBoot3" to "true", "sourceFolder" to "src/main/java",
        "additionalModelTypeAnnotations" to "@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)"))
}
sourceSets { main { java { srcDir("${project.layout.buildDirectory.get()}/generated/openapi-xxx/src/main/java") } } }
tasks.named("compileJava") { dependsOn(openApiGenerateXxx) }
```

### Template generation (replacing Maven templating-maven-plugin)
```kotlin
val generateVersionJava by tasks.registering(Sync::class) {
    from("src/main/java-templates")
    into("${project.layout.buildDirectory.get()}/generated/java-templates")
    filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to mapOf("project.version" to project.version.toString()))
}
sourceSets { main { java { srcDir("${project.layout.buildDirectory.get()}/generated/java-templates") } } }
tasks.named("compileJava") { dependsOn(generateVersionJava) }
```
