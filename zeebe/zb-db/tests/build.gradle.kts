plugins { id("buildlogic.server-conventions") }

dependencies {
  testImplementation(project(":zeebe-db-api"))
  testImplementation(project(":zeebe-db"))
  testImplementation(project(":zeebe-db-writebehind"))
  testImplementation(project(":zeebe-protocol"))
  testImplementation(libs.org.agrona.agrona)
  testImplementation(libs.io.micrometer.micrometer.core)
  testImplementation(libs.junit.junit)
  testRuntimeOnly(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(libs.net.jqwik.jqwik)
}

description = "Zeebe DB Tests"
