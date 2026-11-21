plugins { id("buildlogic.server-conventions") }

java { disableAutoTargetJvm() }

tasks.withType<JavaCompile>().configureEach { options.release.set(8) }

dependencies {
  api(project(":zeebe-xml-model"))
  testImplementation(libs.junit.junit)
  testRuntimeOnly(libs.org.junit.vintage.junit.vintage.engine)
  testImplementation(libs.org.xmlunit.xmlunit.core)
}

description = "Zeebe DMN model API"
