import buildlogic.OpenApiDefaults
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.openapi-conventions")
}

// Mirrors the two Maven executions (cluster, exporter). Each generator task gets its own output
// directory so Gradle can track them independently; the generated sources are then merged into a
// single source directory for compilation.
val openApiClusterOutputDir = layout.buildDirectory.dir("generated/openapi-cluster")
val openApiExporterOutputDir = layout.buildDirectory.dir("generated/openapi-exporter")
val mergedOpenApiJavaDir = layout.buildDirectory.dir("generated/openapi-merged/src/main/java")
val apiSpecsDir = "$projectDir/src/main/resources/api"

private val commonOpenApiConfigOptions =
  mapOf(
    "additionalModelTypeAnnotations" to OpenApiDefaults.NON_NULL_MODEL_TYPE_ANNOTATIONS,
    "hideGenerationTimestamp" to OpenApiDefaults.HIDE_GENERATION_TIMESTAMP,
    "useSpringBoot3" to "true",
    "sourceFolder" to OpenApiDefaults.SOURCE_FOLDER,
  )

private fun GenerateTask.configureCommonOpenApiGeneration(inputPropertyName: String) {
  generatorName.set("spring")
  inputs
    .files(fileTree(apiSpecsDir) { include("**/*.yaml", "**/*.yml") })
    .withPropertyName(inputPropertyName)
    .withPathSensitivity(PathSensitivity.RELATIVE)
  globalProperties.set(OpenApiDefaults.MODEL_ONLY_GLOBAL_PROPERTIES)
  skipValidateSpec.set(false)
  configOptions.set(commonOpenApiConfigOptions)
  modelPackage.set("io.camunda.zeebe.management.cluster")
  // BrokerId is hand written, see src/main/java
  ignoreFileOverride.set("$apiSpecsDir/.openapi-generator-ignore")
}

val openApiGenerateCluster =
  tasks.register<GenerateTask>("openApiGenerateCluster") {
    configureCommonOpenApiGeneration("clusterOpenApiSpecs")
    inputSpec.set("$apiSpecsDir/cluster-api.yaml")
    outputDir.set(openApiClusterOutputDir)
    openapiNormalizer.set(mapOf("SIMPLIFY_ONEOF_ANYOF" to "false"))
    configOptions.set(commonOpenApiConfigOptions + ("useSealed" to "true"))
  }

val openApiGenerateExporter =
  tasks.register<GenerateTask>("openApiGenerateExporter") {
    configureCommonOpenApiGeneration("exporterOpenApiSpecs")
    inputSpec.set("$apiSpecsDir/exporter-api.yaml")
    outputDir.set(openApiExporterOutputDir)
  }

val syncOpenApiGeneratedSources =
  tasks.register<Sync>("syncOpenApiGeneratedSources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    into(mergedOpenApiJavaDir)
    from(openApiGenerateCluster.flatMap { it.outputDir }.map { it.dir("src/main/java") }) {
      exclude("**/BrokerId.java")
    }
    from(openApiGenerateExporter.flatMap { it.outputDir }.map { it.dir("src/main/java") }) {
      exclude("**/BrokerId.java")
    }
  }

// Add merged generated sources to the source set; the task output carries its dependency.
sourceSets { main { java { srcDir(syncOpenApiGeneratedSources) } } }

dependencies {
  api(project(":zeebe-atomix-cluster"))
  implementation(libs.com.fasterxml.jackson.core.jackson.annotations)
  implementation(libs.com.fasterxml.jackson.core.jackson.core)
  implementation(libs.com.fasterxml.jackson.core.jackson.databind)
  implementation(libs.jakarta.validation.jakarta.validation.api)
  implementation(libs.jakarta.annotation.jakarta.annotation.api)
  implementation(libs.org.openapitools.jackson.databind.nullable)
  implementation(libs.io.swagger.core.v3.swagger.annotations.jakarta)
  implementation(libs.org.springframework.spring.core)
  implementation(libs.org.springframework.spring.context)
}

description = "Zeebe Dynamic Cluster Configuration OpenAPI"
