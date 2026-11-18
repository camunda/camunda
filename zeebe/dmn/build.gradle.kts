plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":zeebe-feel-integration"))
  implementation(project(":zeebe-util"))
  // Replaced by the forked :zeebe-dmn-model and :zeebe-xml-model, which keep the same packages.
  api(libs.org.camunda.bpm.extension.dmn.scala.dmn.engine) {
    exclude(group = "org.camunda.bpm.model")
  }
  api(libs.org.scala.lang.scala.library)
  api(project(":zeebe-dmn-model"))
  implementation(project(":zeebe-xml-model"))
  api(libs.org.agrona.agrona)
  api(libs.org.camunda.feel.feel.engine)
  implementation(project(":zeebe-msgpack-core"))
  testImplementation(project(":zeebe-test-util"))
}

description = "Zeebe DMN Engine"
