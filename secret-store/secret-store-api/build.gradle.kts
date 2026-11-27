plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.com.github.ben.manes.caffeine.caffeine)
  implementation(libs.com.fasterxml.jackson.core.jackson.databind)
  implementation(libs.com.fasterxml.jackson.core.jackson.core)
  api(libs.io.micrometer.micrometer.commons)
  api(libs.io.micrometer.micrometer.core)
}

description = "Camunda Secret Store API"
