plugins {
  `kotlin-dsl`
}

group = "io.camunda.gradle"
version = "0.0.0"

repositories {
  mavenCentral()
}

dependencies {
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
  useJUnitPlatform()
}
