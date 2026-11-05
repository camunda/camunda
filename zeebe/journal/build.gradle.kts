plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(libs.org.jspecify.jspecify)
  api(libs.org.agrona.agrona)
  implementation(libs.org.slf4j.slf4j.api)
  api(libs.com.google.guava.guava)
  api(libs.io.micrometer.micrometer.commons)
  api(libs.io.micrometer.micrometer.core)
  implementation(project(":zeebe-log-schemas"))
  implementation(project(":zeebe-util"))
  implementation(project(":zeebe-micrometer"))
  api(libs.com.github.jnr.jnr.ffi)
  api(libs.com.github.jnr.jnr.constants)
  testImplementation(libs.com.github.jnr.jnr.posix)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(project(":zeebe-test-util"))
}

description = "Zeebe Journal"
