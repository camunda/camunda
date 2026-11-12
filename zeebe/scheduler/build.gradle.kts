plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.test-jar-conventions")
  id("buildlogic.parallel-test-classes-conventions")
  id("buildlogic.mutation-testing-conventions")
}

dependencies {
  implementation(project(":camunda-cluster"))
  implementation(libs.org.jspecify.jspecify)
  api(libs.org.agrona.agrona)
  implementation(libs.org.slf4j.slf4j.api)
  api(libs.io.micrometer.micrometer.commons)
  api(libs.io.micrometer.micrometer.core)
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-micrometer"))
  implementation(libs.org.jetbrains.annotations)
  testImplementation(libs.junit.junit)
  testRuntimeOnly(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.awaitility.awaitility)
  testImplementation(libs.org.jetbrains.lincheck.lincheck)
  compileOnly(libs.com.github.spotbugs.spotbugs.annotations)
}

description = "Zeebe Scheduler"
