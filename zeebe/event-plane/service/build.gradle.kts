plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-event-plane-common"))
  implementation(libs.com.fasterxml.jackson.core.jackson.core)
  implementation(libs.com.fasterxml.jackson.core.jackson.databind)
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.org.slf4j.slf4j.api)
  testImplementation(libs.org.awaitility.awaitility)
}

description = "Zeebe Event Plane Service"
