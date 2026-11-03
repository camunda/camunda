plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-protocol"))
  api(project(":zeebe-protocol-impl"))
  api(project(":zeebe-util"))
  api(libs.org.agrona.agrona)
  implementation(libs.org.jspecify.jspecify)
  testImplementation(project(":zeebe-journal"))
  testImplementation(project(":zeebe-atomix-cluster"))
  testImplementation(libs.net.jqwik.jqwik)
  testImplementation(libs.net.jqwik.jqwik.api)
}

description = "Zeebe Log Records"
