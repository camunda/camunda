plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-protocol-impl"))
  api(project(":zeebe-stream-platform"))
  implementation(project(":zeebe-msgpack-value"))
  implementation(libs.io.camunda.security.library.api)
  implementation(libs.io.camunda.security.library.validation)
  api(libs.org.agrona.agrona)
}

description = "Zeebe Workflow Engine Common: contract types shared by the engine and its API"
