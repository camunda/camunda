plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.parallel-test-classes-conventions")
}

dependencies {
  api(project(":zeebe-metadata-store-api"))
  api(project(":zeebe-scheduler"))
  implementation(project(":zeebe-util"))
  implementation(libs.org.jspecify.jspecify)

  testImplementation(project(":zeebe-metadata-store-testkit", configuration = "tests"))
  testImplementation(libs.org.awaitility.awaitility)
}

description = "Zeebe Cluster Metadata Store In-Memory"
