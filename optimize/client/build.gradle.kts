// Does not use buildlogic.frontend-webjar-conventions because:
//  1. Uses Yarn, not npm (convention uses NpmTask)
//  2. No webjar packaging — processResources is not wired to copy build output into a JAR

import buildlogic.frontendBuildSkipped
import buildlogic.skipWhen
import com.github.gradle.node.NodeExtension
import com.github.gradle.node.yarn.task.YarnTask
import io.camunda.gradle.pom.PomResolver
import io.camunda.gradle.pom.resolvePomProperty

plugins {
  id("buildlogic.server-conventions")
  id("buildlogic.optimize-conventions")
  id("com.github.node-gradle.node")
}

val parentPomVersions =
  PomResolver(providers.fileContents(layout.settingsDirectory.file("parent/pom.xml")).asText.get())
    .properties()

extensions.configure<NodeExtension> {
  download.set(true)
  version.set(resolvePomProperty("version.node", parentPomVersions).removePrefix("v"))
  yarnVersion.set(resolvePomProperty("version.yarn", parentPomVersions).removePrefix("v"))
  distBaseUrl.set(null as String?)
  workDir.set(layout.projectDirectory.dir(".node/nodejs"))
  yarnWorkDir.set(layout.projectDirectory.dir(".node/yarn"))
  nodeProjectDir.set(layout.projectDirectory)
}

val skipFrontendBuild = frontendBuildSkipped()

// The plugin's setup tasks are dependencies of the guarded tasks below and would still download
// Node and Yarn when those are skipped.
tasks.named("nodeSetup") { skipWhen(skipFrontendBuild) }

tasks.named("yarnSetup") { skipWhen(skipFrontendBuild) }

val yarnInstall =
  tasks.register<YarnTask>("yarnInstall") {
    skipWhen(skipFrontendBuild)
    dependsOn(tasks.named("yarnSetup"))
    args.set(listOf("install"))
  }

val yarnBuild =
  tasks.register<YarnTask>("yarnBuild") {
    skipWhen(skipFrontendBuild)
    dependsOn(yarnInstall)
    args.set(listOf("build"))
  }

tasks.named("processResources") { mustRunAfter(yarnBuild) }

group = "io.camunda.optimize"

description = "Optimize Client"
