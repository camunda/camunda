plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-util"))
  api(project(":camunda-cluster"))
  api(libs.io.micrometer.micrometer.core)
  api(libs.io.micrometer.micrometer.commons)
  api(libs.com.github.ben.manes.caffeine.caffeine)
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.org.slf4j.slf4j.api)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.mockito.mockito.junit.jupiter)
  testImplementation(libs.io.micrometer.micrometer.registry.prometheus)
  testImplementation(libs.io.prometheus.prometheus.metrics.model)
  testImplementation(libs.org.awaitility.awaitility)
}

description = "Zeebe Micrometer"
