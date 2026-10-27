plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-workflow-engine-state-mutable-api"))
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.org.apache.commons.commons.lang3)
}

description = "Zeebe Workflow Engine Appliers: event appliers that fold records into state"
