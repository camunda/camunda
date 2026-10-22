plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":zeebe-event-plane-segment"))
  implementation(project(":zeebe-event-plane-consumer"))
  implementation(project(":zeebe-event-plane-common"))
  implementation(project(":zeebe-event-plane-service"))
  implementation(project(":zeebe-protocol"))
  implementation(libs.org.jspecify.jspecify)
  runtimeOnly(project(":zeebe-event-plane-maintenance"))
  runtimeOnly(libs.org.apache.logging.log4j.log4j.slf4j2.impl)
  runtimeOnly(libs.org.apache.logging.log4j.log4j.core)
}

description = "Zeebe Event Plane Demo"
