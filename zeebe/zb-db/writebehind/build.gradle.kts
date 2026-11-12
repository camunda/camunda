plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-db-api"))
  api(project(":zeebe-protocol"))
  api(project(":zeebe-util"))
  api(libs.org.agrona.agrona)
  api(libs.io.micrometer.micrometer.core)
  api(libs.com.github.ben.manes.caffeine.caffeine)
  implementation(libs.org.slf4j.slf4j.api)
  testImplementation(project(":zeebe-msgpack-value"))
  testImplementation(libs.org.awaitility.awaitility)
}

description = "Zeebe DB Write-Behind"
