plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(project(":zeebe-db-api"))
  implementation(project(":zeebe-protocol"))
  implementation(libs.org.agrona.agrona)
  implementation(libs.org.ehcache.ehcache) {
    exclude(group = "org.glassfish.jaxb", module = "jaxb-runtime")
  }
  testImplementation(libs.org.mockito.mockito.core)
}

description = "Zeebe DB Cache"
