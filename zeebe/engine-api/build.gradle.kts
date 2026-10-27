plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-stream-platform"))
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-db"))
  implementation(project(":zeebe-msgpack-value"))
  implementation(libs.io.camunda.security.library.api)
  implementation(libs.io.camunda.security.library.validation)
  implementation(libs.org.slf4j.slf4j.api)
  api(libs.org.agrona.agrona)
  api(libs.io.micrometer.micrometer.core)
  api(libs.io.micrometer.micrometer.commons)
}

description = "Zeebe Workflow Engine API: the engine surface consumed by the broker"
