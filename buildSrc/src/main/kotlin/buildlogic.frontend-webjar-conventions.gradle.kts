import com.github.gradle.node.NodeExtension
import com.github.gradle.node.npm.task.NpmTask
import org.gradle.api.provider.Provider
import org.gradle.language.jvm.tasks.ProcessResources

fun Provider<String>.asEnabledFlag(): Provider<Boolean> =
    map { value -> value.isEmpty() || value.toBoolean() }

plugins {
    id("buildlogic.server-conventions")
    id("com.github.node-gradle.node")
}

interface FrontendWebjarExtension {
    val frontendBuildDirectory: DirectoryProperty
    val frontendPackagedDirectory: DirectoryProperty
    val resourceTargetPath: Property<String>
}

val frontendWebjar = extensions.create<FrontendWebjarExtension>("frontendWebjar")
val frontendBuildDirectory = frontendWebjar.frontendBuildDirectory
val frontendPackagedDirectory = frontendWebjar.frontendPackagedDirectory
val resourceTargetPath = frontendWebjar.resourceTargetPath

extensions.configure<NodeExtension> {
    download.set(true)
    version.set("24.13.0")
    npmVersion.set("11.9.0")
    distBaseUrl.set(null as String?)
    workDir.set(rootProject.layout.projectDirectory.dir(".gradle/nodejs/${project.name}"))
    npmWorkDir.set(rootProject.layout.projectDirectory.dir(".gradle/npm/${project.name}"))
    nodeProjectDir.set(layout.projectDirectory)
}

val skipFrontendBuild =
    providers.gradleProperty("skip.fe.build")
        .orElse(providers.gradleProperty("quickly"))
        .asEnabledFlag()
        .orElse(false)

val npmVersionPackage by tasks.registering(NpmTask::class) {
    enabled = !skipFrontendBuild.get()
    dependsOn(tasks.named("npmSetup"))
    args.set(listOf("version", project.version.toString(), "--no-git-tag-version", "--allow-same-version"))
    inputs.property("projectVersion", project.version)
    outputs.file(layout.projectDirectory.file("package.json"))
    outputs.cacheIf { true }
}

val npmCi by tasks.registering(NpmTask::class) {
    enabled = !skipFrontendBuild.get()
    dependsOn(npmVersionPackage)
    args.set(listOf("ci"))
    inputs.files(
        layout.projectDirectory.file("package.json"),
        layout.projectDirectory.file("package-lock.json"),
    )
    outputs.dir(layout.projectDirectory.dir("node_modules"))
    // node_modules is too large for remote build cache; up-to-date checks are sufficient
    outputs.cacheIf { false }
}

val npmBuild by tasks.registering(NpmTask::class) {
    enabled = !skipFrontendBuild.get()
    dependsOn(npmCi)
    args.set(listOf("run", "build"))
    inputs
        .files(
            provider {
                val excludes = mutableListOf("node_modules/**", "target/**", ".gradle/**")
                if (frontendBuildDirectory.isPresent) {
                    runCatching {
                        val relPath =
                            frontendBuildDirectory.get().asFile
                                .relativeTo(layout.projectDirectory.asFile)
                                .path
                        excludes.add("$relPath/**")
                    }
                }
                fileTree(layout.projectDirectory) { exclude(excludes) }
            }
        )
        .withPropertyName("sourceFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(frontendBuildDirectory)
    outputs.cacheIf { true }
}

tasks.named<ProcessResources>("processResources") {
    if (!skipFrontendBuild.get()) {
        dependsOn(npmBuild)
    }
    from(frontendPackagedDirectory.orElse(frontendBuildDirectory)) {
        into(resourceTargetPath)
    }
}
