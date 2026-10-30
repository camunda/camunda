plugins { id("buildlogic.server-conventions") }

dependencies {
  implementation(project(":zeebe-cluster-config-api"))
  implementation(project(":zeebe-cluster-config-openapi"))
  implementation(project(":zeebe-atomix-cluster"))
  implementation(project(":camunda-cluster"))
  implementation(project(":zeebe-util"))
  implementation(libs.org.springframework.boot.spring.boot.actuator)
  implementation(libs.org.springframework.spring.beans)
  implementation(libs.org.springframework.spring.context)
  implementation(libs.org.springframework.spring.web)
  implementation(libs.org.jspecify.jspecify)
  implementation(libs.jakarta.servlet.jakarta.servlet.api)

  testImplementation(project(":zeebe-cluster-config-impl"))
  testImplementation(libs.org.mockito.mockito.core)
}

description = "Zeebe Dynamic Cluster Configuration REST"
