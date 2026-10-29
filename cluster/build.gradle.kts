plugins { id("buildlogic.server-conventions") }

description = "Unified Configuration API"

dependencies {
  implementation(libs.com.google.guava.guava)
  implementation(libs.org.jspecify.jspecify)
}
