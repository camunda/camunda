plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-protocol"))
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Cluster Behavior"
