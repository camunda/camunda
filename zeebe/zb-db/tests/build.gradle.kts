plugins { id("buildlogic.server-conventions") }

dependencies {
  testImplementation(project(":zeebe-db-api"))
  testImplementation(project(":zeebe-db-rocksdb"))
  testImplementation(project(":zeebe-db-cache"))
  testImplementation(project(":zeebe-protocol"))
  testImplementation(libs.io.micrometer.micrometer.core)
  testImplementation(libs.junit.junit)
  testRuntimeOnly(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(libs.net.jqwik.jqwik)
}

description = "Zeebe DB Tests"
