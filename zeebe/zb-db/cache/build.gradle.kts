plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-db-api"))
  implementation(project(":zeebe-protocol"))
  implementation(libs.org.agrona.agrona)
}

description = "Zeebe DB Cache"
