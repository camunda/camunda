plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-util"))
  api(project(":zeebe-protocol"))
  api(project(":zeebe-db"))
  api(project(":camunda-search-client"))
  api(project(":camunda-secret-store-api"))
  api(project(":camunda-security-core"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-bpmn-model"))
  api(project(":zeebe-stream-platform"))
  api(libs.org.agrona.agrona)
}

description = "Zeebe Workflow Engine API"
