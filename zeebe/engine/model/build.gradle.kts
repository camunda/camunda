plugins { id("buildlogic.server-conventions") }

dependencies {
  api(project(":zeebe-workflow-engine-common"))
  api(project(":zeebe-bpmn-model"))
  api(project(":zeebe-expression-language"))
  api(project(":zeebe-feel-tagged-parameters"))
  api(project(":zeebe-msgpack-value"))
  api(project(":zeebe-protocol"))
  api(project(":zeebe-protocol-impl"))
  api(project(":zeebe-util"))
  api(project(":zeebe-db"))
  api(project(":camunda-secret-store-api"))
  api(project(":camunda-search-domain"))
  api(libs.org.agrona.agrona)
  api(libs.org.camunda.bpm.model.camunda.xml.model)
  api(libs.org.camunda.feel.feel.engine)
  api(libs.org.scala.lang.scala.library)
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.org.slf4j.slf4j.api)
  implementation(libs.org.apache.commons.commons.lang3)
  implementation(libs.com.cronutils.cron.utils)
  implementation(libs.com.fasterxml.jackson.core.jackson.annotations)
  implementation(project(":zeebe-msgpack-core"))
}

description = "Zeebe Workflow Engine Model: BPMN/DMN model transformation and validation"
