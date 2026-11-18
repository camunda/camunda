plugins { id("buildlogic.server-conventions") }

java { disableAutoTargetJvm() }

tasks.withType<JavaCompile>().configureEach { options.release.set(8) }

dependencies {
  compileOnly(libs.org.junit.jupiter.junit.jupiter.api)
  compileOnly(libs.org.assertj.assertj.core)
}

description = "Zeebe XML model API"
