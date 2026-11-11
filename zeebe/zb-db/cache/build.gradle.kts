plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-db-api"))
  implementation(project(":zeebe-protocol"))
  implementation(project(":zeebe-util"))
  implementation(libs.org.agrona.agrona)
  implementation(libs.io.micrometer.micrometer.core)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.org.ehcache.ehcache) {
    exclude(group = "org.glassfish.jaxb", module = "jaxb-runtime")
  }
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.awaitility.awaitility)
}

description = "Zeebe DB Cache"
