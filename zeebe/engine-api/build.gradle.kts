plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-workflow-engine-common"))
  api(project(":zeebe-db"))
  api(project(":zeebe-protocol"))
  api(project(":camunda-search-client"))
  api(project(":camunda-secret-store-api"))
  api(project(":camunda-security-core"))
  api(project(":zeebe-util"))
  api(project(":zeebe-stream-platform"))
  implementation(project(":zeebe-workflow-engine"))
  implementation(project(":zeebe-expression-language"))
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Workflow Engine API: the only engine surface the broker compiles against"
