plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.test-jar-conventions")
}

dependencies {
  testImplementation(project(":zeebe-metadata-store-api"))
  testImplementation(project(":zeebe-scheduler"))
  testImplementation(libs.org.awaitility.awaitility)
}

description = "Zeebe Cluster Metadata Store Testkit"
