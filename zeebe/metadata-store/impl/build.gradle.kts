plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.parallel-test-classes-conventions")
}

dependencies {
  api(project(":zeebe-metadata-store-api"))
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Cluster Metadata Store Implementation"
