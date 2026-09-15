plugins {
  `base`
  `kotlin-dsl`
}

repositories {
  mavenCentral()
}

gradlePlugin {
  plugins {
    create("settingsPomResolver") {
      id = "io.camunda.gradle.settings-pom-resolver"
      implementationClass = "io.camunda.gradle.pom.SettingsPomResolverPlugin"
    }
  }
}

dependencies {
  implementation(project(":pom-resolution"))
}
