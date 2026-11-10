plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.test-jar-conventions")
}

dependencies {
  implementation(libs.org.jspecify.jspecify)
}

description = "Zeebe Mailbox API"
