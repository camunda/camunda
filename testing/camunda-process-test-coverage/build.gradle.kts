import org.gradle.api.provider.Provider

fun Provider<String>.asEnabledFlag(): Provider<Boolean> =
    map { value -> value.isEmpty() || value.toBoolean() }

plugins {
    id("buildlogic.frontend-webjar-conventions")
}

val skipFrontendBuild =
    providers.gradleProperty("skip.fe.build")
        .orElse(providers.gradleProperty("quickly"))
        .asEnabledFlag()
        .orElse(true)
val skipProcessTestFrontendBuild =
    providers.gradleProperty("skip.fe.process-test.build").asEnabledFlag().orElse(true)
val shouldBuildFrontend = !skipFrontendBuild.get() || !skipProcessTestFrontendBuild.get()

frontendWebjar {
    frontendBuildDirectory.set(layout.buildDirectory.dir("generated-frontend-resources"))
    resourceTargetPath.set("")
}

tasks.named<com.github.gradle.node.npm.task.NpmTask>("npmVersionPackage") {
    enabled = false
}

tasks.named<com.github.gradle.node.npm.task.NpmTask>("npmCi") {
    enabled = shouldBuildFrontend
}

tasks.named<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    enabled = shouldBuildFrontend
    environment.put(
        "BUILD_PATH",
        layout.buildDirectory.dir("generated-frontend-resources/coverage").get().asFile.absolutePath,
    )
}

tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("processResources") {
    if (shouldBuildFrontend) {
        dependsOn(tasks.named("npmBuild"))
    }
}

description = "Camunda Process Test Coverage Frontend (New)"
