plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-workflow-engine-state-api"))
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Workflow Engine Mutable State API: the write interfaces that only appliers and migrations use"
