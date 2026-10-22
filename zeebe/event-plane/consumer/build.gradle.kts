plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-event-plane-segment"))
  api(project(":zeebe-event-plane-common"))
  api(project(":zeebe-protocol"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-logstreams"))
  implementation(project(":zeebe-util"))
  implementation(libs.org.agrona.agrona)
  implementation(libs.com.fasterxml.jackson.core.jackson.core)
  implementation(libs.com.fasterxml.jackson.core.jackson.databind)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.org.jspecify.jspecify)
  testImplementation(libs.org.awaitility.awaitility)
  testImplementation(project(":zeebe-journal"))
  testImplementation(project(":zeebe-atomix-cluster"))
  testImplementation(libs.io.micrometer.micrometer.core)
}

description = "Zeebe Event Plane Consumer"
