plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.parallel-test-classes-conventions")
}

dependencies {
  api(project(":zeebe-scheduler"))
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Cluster Metadata Store API"
