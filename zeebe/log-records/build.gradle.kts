plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-protocol"))
  api(project(":zeebe-protocol-impl"))
  api(project(":zeebe-util"))
  api(libs.org.agrona.agrona)
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Log Records"
