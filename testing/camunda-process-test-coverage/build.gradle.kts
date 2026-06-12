plugins {
    id("buildlogic.server-conventions")
}

java {
    disableAutoTargetJvm()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

dependencies {
    implementation(project(":camunda-client-java"))
    implementation(project(":zeebe-bpmn-model"))
    implementation(libs.org.slf4j.slf4j.api)
    implementation(libs.org.camunda.bpm.model.camunda.dmn.model)
    implementation(libs.org.camunda.bpm.model.camunda.xml.model)
    implementation(libs.com.fasterxml.jackson.core.jackson.databind)
    implementation(libs.com.fasterxml.jackson.core.jackson.annotations)
    implementation(libs.com.fasterxml.jackson.core.jackson.core)
    implementation(libs.javax.annotation.javax.annotation.api)
    implementation(libs.commons.io.commons.io)
    compileOnly(libs.org.immutables.value)
    annotationProcessor(libs.org.immutables.value)
    testImplementation(libs.org.junit.jupiter.junit.jupiter.api.x1)
    testImplementation(libs.org.assertj.assertj.core)
    testImplementation(libs.org.mockito.mockito.core)
    testImplementation(libs.org.mockito.mockito.junit.jupiter)
}

description = "Camunda Process Test Coverage"
