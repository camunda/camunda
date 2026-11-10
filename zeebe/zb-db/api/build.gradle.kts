plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-protocol"))
  api(project(":zeebe-util"))
  api(libs.io.micrometer.micrometer.core)
  api(libs.org.agrona.agrona)
  testImplementation(libs.org.mockito.mockito.core)
}

description = "Zeebe DB API"
