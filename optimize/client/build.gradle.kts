// Does not use buildlogic.frontend-webjar-conventions because:
//  1. Uses Yarn, not npm (convention uses NpmTask)
//  2. No webjar packaging — processResources is not wired to copy build output into a JAR

import buildlogic.parsePomProperties
import buildlogic.pomVersion
import com.github.gradle.node.NodeExtension
import com.github.gradle.node.yarn.task.YarnTask
import org.gradle.api.provider.Provider

fun Provider<String>.asEnabledFlag(): Provider<Boolean> = map { value ->
  value.isEmpty() || value.toBoolean()
}

plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.optimize-conventions")
  id("com.github.node-gradle.node")
}

val parentPomVersions =
  parsePomProperties(
    providers.fileContents(rootProject.layout.projectDirectory.file("parent/pom.xml")).asText.get()
  )

extensions.configure<NodeExtension> {
  download.set(true)
  version.set(pomVersion(parentPomVersions, "version.node").removePrefix("v"))
  yarnVersion.set(pomVersion(parentPomVersions, "version.yarn").removePrefix("v"))
  distBaseUrl.set(null as String?)
  workDir.set(layout.projectDirectory.dir(".node/nodejs"))
  yarnWorkDir.set(layout.projectDirectory.dir(".node/yarn"))
  nodeProjectDir.set(layout.projectDirectory)
}

val skipFrontendBuild =
  providers
    .gradleProperty("skip.fe.build")
    .orElse(providers.gradleProperty("quickly"))
    .asEnabledFlag()
    .orElse(false)

val yarnInstall =
  tasks.register<YarnTask>("yarnInstall") {
    enabled = !skipFrontendBuild.get()
    dependsOn(tasks.named("yarnSetup"))
    args.set(listOf("install"))
  }

val yarnBuild =
  tasks.register<YarnTask>("yarnBuild") {
    enabled = !skipFrontendBuild.get()
    dependsOn(yarnInstall)
    args.set(listOf("build"))
  }

tasks.named("processResources") { mustRunAfter(yarnBuild) }

group = "io.camunda.optimize"

description = "Optimize Client"
