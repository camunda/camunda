plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-event-plane-segment"))
  api(project(":zeebe-event-plane-common"))
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.org.slf4j.slf4j.api)
  testImplementation(libs.net.jqwik.jqwik)
  testImplementation(libs.net.jqwik.jqwik.api)
  testImplementation(project(":zeebe-test-util"))
  testImplementation(libs.org.apache.logging.log4j.log4j.api)
  testImplementation(project(":zeebe-journal"))
  testImplementation(project(":zeebe-atomix-cluster"))
  testImplementation(project(":zeebe-util"))
  testImplementation(libs.org.agrona.agrona)
  testImplementation(libs.io.micrometer.micrometer.core)
}

description = "Zeebe Event Plane Publisher"
