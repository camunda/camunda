plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-workflow-engine-model"))
  api(project(":zeebe-db"))
  api(project(":zeebe-dmn"))
  api(project(":zeebe-stream-platform"))
  api(project(":camunda-security-core"))
  api(libs.io.camunda.security.library.api)
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.org.slf4j.slf4j.api)
}

description = "Zeebe Workflow Engine State API: immutable and mutable state interfaces and persisted value types"
