plugins { `kotlin-dsl` }

dependencies {
  compileOnly(libs.assertj.assertions.generator)
  // Mirrors Maven's dependencyManagement of assertj-core. The generator declares the range
  // [2.1.0, 2.99.0], and resolving a range lists versions in every repository, so a single
  // throttled repository would fail the build.
  constraints {
    compileOnly(libs.assertj.core) { version { strictly(libs.versions.assertj.get()) } }
  }
  implementation(project(":pom-resolution"))
  implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.4")
  implementation("com.github.node-gradle:gradle-node-plugin:7.1.0")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.3.0")
  implementation("com.google.protobuf:protobuf-gradle-plugin:0.10.0")
  implementation(libs.openapi.generator.plugin)
  implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
  implementation("org.gradle:test-retry-gradle-plugin:1.6.6")

  testImplementation(gradleTestKit())
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }
