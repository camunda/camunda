import io.camunda.gradle.pom.PomResolver
import io.camunda.gradle.pom.resolvePomProperty

buildscript {
  dependencies {
    classpath("io.camunda.gradle:pom-resolution:0.0.0")
  }
}

val pomVersions: Map<String, String> =
  PomResolver(
      providers.fileContents(layout.projectDirectory.file("../parent/pom.xml")).asText.get()
    )
    .properties()

fun pomVersion(key: String): String = resolvePomProperty(key, pomVersions)

plugins { `kotlin-dsl` }

repositories {
  gradlePluginPortal()
  mavenCentral()
}

dependencies {
  implementation("io.camunda.gradle:pom-resolution:0.0.0")
  implementation("com.diffplug.spotless:spotless-plugin-gradle:8.8.0")
  implementation("com.github.node-gradle:gradle-node-plugin:7.1.0")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.3.0")
  implementation("com.google.protobuf:protobuf-gradle-plugin:0.10.0")
  implementation(
    "org.openapitools:openapi-generator-gradle-plugin:${pomVersion("plugin.version.openapi-generator")}"
  )
  implementation("com.gradleup.shadow:shadow-gradle-plugin:9.4.2")
  implementation("org.gradle:test-retry-gradle-plugin:1.6.6")
  implementation("uk.co.real-logic:sbe-tool:${pomVersion("version.sbe")}")
}
