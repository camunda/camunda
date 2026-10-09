plugins { `kotlin-dsl` }

repositories {
  gradlePluginPortal()
  mavenCentral()
}

dependencies {
  compileOnly(libs.assertj.assertions.generator)
  implementation(project(":pom-resolution"))
  implementation("com.diffplug.spotless:spotless-plugin-gradle:8.8.0")
  implementation("com.github.node-gradle:gradle-node-plugin:7.1.0")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.3.0")
  implementation("com.google.protobuf:protobuf-gradle-plugin:0.10.0")
  implementation(libs.openapi.generator.plugin)
  implementation("com.gradleup.shadow:shadow-gradle-plugin:9.4.2")
  implementation("org.gradle:test-retry-gradle-plugin:1.6.6")

  testImplementation(gradleTestKit())
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
