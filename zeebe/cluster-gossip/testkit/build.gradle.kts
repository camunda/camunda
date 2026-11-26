plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-cluster-gossip-api"))
  api(project(":camunda-cluster"))
  implementation(libs.org.jspecify.jspecify)
  api(libs.org.junit.jupiter.junit.jupiter.api)
  api(libs.org.assertj.assertj.core)
}

description = "Zeebe Cluster Gossip Testkit"
