plugins { id("buildlogic.server-conventions") }

java { disableAutoTargetJvm() }

tasks.withType<JavaCompile>().configureEach { options.release.set(8) }

dependencies {
  compileOnly(libs.junit.junit)
  compileOnly(libs.org.assertj.assertj.core)
}

description = "Zeebe XML model API"
