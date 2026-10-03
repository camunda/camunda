plugins {
  `kotlin-dsl`
}

group = "io.camunda.gradle"
version = "0.0.0"

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
  useJUnitPlatform()
}
