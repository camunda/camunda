import buildlogic.ArtifactKind
import buildlogic.artifactKind

plugins { id("buildlogic.server-conventions") }

// Build-time tooling (GenerateDefaults) that is run against the Spring configuration metadata. It is
// kept out of `main` so it is not packaged in the module jar.
val buildTools =
  sourceSets.create("build") {
    java.srcDir("src/build")
    compileClasspath += sourceSets["main"].output
    runtimeClasspath += output + compileClasspath
  }

// Inherit the main dependencies (and the BOM constraints applied to `implementation`).
configurations.named(buildTools.implementationConfigurationName) {
  extendsFrom(configurations.implementation.get())
}

sourceSets.named("test") {
  compileClasspath += buildTools.output
  runtimeClasspath += buildTools.output
}

// Mirrors dist's exec:java@generate-default-config. Not wired into assemble/check, like the Maven
// execution bound to phase=none; :dist copies the result into its sources and verifies it.
val metadataFile =
  layout.buildDirectory.file("classes/java/main/META-INF/spring-configuration-metadata.json")
val generatedDefaultsFile = layout.buildDirectory.file("generated/default-config/defaults.yaml")

val generateDefaultConfig =
  tasks.register<JavaExec>("generateDefaultConfig") {
    group = "build tools"
    description = "Generates defaults.yaml from the Spring configuration metadata"
    classpath = buildTools.runtimeClasspath
    mainClass.set("io.camunda.configuration.GenerateDefaults")
    dependsOn(tasks.named("compileJava"))
    inputs.file(metadataFile)
    outputs.file(generatedDefaultsFile)
    // JavaExec is not cacheable by default; opt in so unchanged inputs hit the build cache.
    outputs.cacheIf { true }
    args(metadataFile.get().asFile.absolutePath, generatedDefaultsFile.get().asFile.absolutePath)
  }

// Consumed by :dist, which owns the committed copy of the file.
val defaultConfig =
  configurations.create("defaultConfig") {
    isCanBeConsumed = true
    isCanBeResolved = false
    artifactKind(project, ArtifactKind.DEFAULT_CONFIG)
  }

artifacts { add(defaultConfig.name, generatedDefaultsFile) { builtBy(generateDefaultConfig) } }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(libs.org.springframework.boot.spring.boot)
  annotationProcessor(libs.org.springframework.boot.spring.boot.configuration.processor)
  api(libs.org.springframework.spring.beans)
  api(libs.org.springframework.spring.context)
  implementation(project(":operate-common"))
  implementation(project(":zeebe-gateway"))
  implementation(project(":zeebe-broker"))
  implementation(project(":zeebe-cluster-config-api"))
  implementation(project(":zeebe-scheduler"))
  implementation(project(":zeebe-backup-store-azure"))
  implementation(project(":zeebe-backup-store-s3"))
  implementation(project(":zeebe-db-rocksdb"))
  implementation(project(":camunda-db-rdbms"))
  implementation(project(":zeebe-gateway-rest"))
  implementation(project(":zeebe-atomix-messaging-api"))
  implementation(project(":zeebe-workflow-engine"))
  implementation(project(":zeebe-exporter-common"))
  implementation(project(":zeebe-exporter-config-support"))
  implementation(project(":dynamic-node-id-provider"))
  implementation(project(":zeebe-util"))
  implementation(project(":camunda-search-domain"))
  implementation(project(":document-store"))
  implementation(project(":document-api"))
  implementation(project(":camunda-spring-utils"))
  implementation(libs.org.slf4j.slf4j.api)
  api(libs.org.springframework.spring.core)
  implementation(project(":camunda-exporter"))
  implementation(project(":rdbms-exporter"))
  implementation(project(":camunda-search-client-connect"))
  api(project(":camunda-cluster"))
  implementation(libs.io.camunda.security.library.spring.boot.starter)
  implementation(libs.io.camunda.security.library.api)
  api(project(":camunda-schema-manager"))
  api(libs.com.fasterxml.jackson.core.jackson.databind)
  api(libs.com.fasterxml.jackson.core.jackson.annotations)
  api(libs.org.apache.commons.commons.text)
  testImplementation(project(":zeebe-atomix-utils"))
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.springframework.spring.test)
  testImplementation(libs.org.springframework.boot.spring.boot.test)
  testImplementation(libs.org.apache.logging.log4j.log4j.core.test)
  testImplementation(libs.org.apache.logging.log4j.log4j.api)
  testImplementation(project(":zeebe-backup"))
  testImplementation(project(":zeebe-test-util"))
  testImplementation(project(":webapps-schema"))
}

description = "Unified Configuration"
