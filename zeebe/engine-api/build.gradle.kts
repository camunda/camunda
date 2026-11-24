plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-protocol"))
  implementation(project(":zeebe-protocol-impl"))
  implementation(project(":zeebe-bpmn-model"))
  api(libs.org.agrona.agrona)
}

description = "Zeebe Workflow Engine API"
