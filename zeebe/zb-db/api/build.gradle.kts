plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-protocol"))
  api(project(":zeebe-util"))
  api(libs.org.agrona.agrona)
  api(libs.io.micrometer.micrometer.core)
}

description = "Zeebe DB API"
