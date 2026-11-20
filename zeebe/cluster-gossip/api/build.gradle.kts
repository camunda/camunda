plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":camunda-cluster"))
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Cluster Gossip API"
