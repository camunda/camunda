plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":zeebe-event-plane-segment"))
  api(project(":zeebe-event-plane-common"))
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.org.slf4j.slf4j.api)
  testImplementation(project(":zeebe-journal"))
  testImplementation(project(":zeebe-atomix-cluster"))
  testImplementation(project(":zeebe-util"))
  testImplementation(libs.org.agrona.agrona)
  testImplementation(libs.io.micrometer.micrometer.core)
}

description = "Zeebe Event Plane Maintenance"
