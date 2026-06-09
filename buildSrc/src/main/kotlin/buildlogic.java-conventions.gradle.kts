import com.diffplug.gradle.spotless.SpotlessExtension
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider

fun Provider<String>.asEnabledFlag(): Provider<Boolean> =
    map { value -> value.isEmpty() || value.toBoolean() }

plugins {
    `java-library`
    `maven-publish`
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

group = "io.camunda"
version = "8.10.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val googleJavaFormatVersion =
    versionCatalog.findVersion("com-google-googlejavaformat-google-java-format").get().requiredVersion
val isCi = providers.environmentVariable("CI")
    .map { it.equals("true", ignoreCase = true) }
    .getOrElse(false)
val quickly = providers.gradleProperty("quickly").asEnabledFlag().orElse(false)

dependencies {
    add("implementation", platform(versionCatalog.findLibrary("org-junit-junit-bom").get()))
    add("errorprone", versionCatalog.findLibrary("com-google-errorprone-error-prone-core").get())
    add("errorprone", versionCatalog.findLibrary("com-uber-nullaway-nullaway").get())
    add("testImplementation", versionCatalog.findLibrary("org-junit-jupiter-junit-jupiter-api").get())
    add("testImplementation", versionCatalog.findLibrary("org-junit-jupiter-junit-jupiter-params").get())
    add("testRuntimeOnly", versionCatalog.findLibrary("org-junit-jupiter-junit-jupiter-engine").get())
    add("testRuntimeOnly", versionCatalog.findLibrary("org-junit-platform-junit-platform-launcher").get())
    add("testImplementation", versionCatalog.findLibrary("org-assertj-assertj-core").get())
}

extensions.configure<SpotlessExtension> {
    isEnforceCheck = isCi

    java {
        target("src/**/*.java")
        googleJavaFormat(googleJavaFormatVersion).style("GOOGLE")
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "utf-8"
    options.compilerArgs.add("-parameters")
    options.errorprone.isEnabled.set(name == "compileJava")
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(
        listOf(
            "-XDcompilePolicy=simple",
            "--should-stop=ifError=FLOW",
            "-XDaddTypeAnnotationsToSymbol=true",
        )
    )
    options.errorprone.disableAllChecks.set(true)
    options.errorprone.error("NullAway")
    options.errorprone.excludedPaths.set(
        ".*/build/(generated|generated-sources|generated-test-sources|tmp|classes)/.*"
    )
    options.errorprone.option("NullAway:JSpecifyMode", "true")
    options.errorprone.option("NullAway:OnlyNullMarked", "true")
    options.errorprone.option("NullAway:AcknowledgeRestrictiveAnnotations", "true")
}

tasks.withType<Javadoc> {
    options.encoding = "utf-8"
}

val skipRandomTests = providers.gradleProperty("skip.random.tests").isPresent
val parallelTests = providers.gradleProperty("parallel.tests").isPresent
val junitThreadCount = providers.gradleProperty("junit.thread.count").getOrElse("2")
val testJvmMaxHeap = providers.gradleProperty("test.jvm.maxheap").orNull
val testMaxForks = providers.gradleProperty("test.max.forks").orNull?.toInt() ?: 1

val itPatterns = listOf(
    "**/IT*.class",
    "**/*IT.class",
    "**/*ITCase.class",
)

tasks.withType<Test>().configureEach {
    enabled = !quickly.get()
    maxParallelForks = testMaxForks
    // Pass the name of Gradle's internal per-worker ID property so TestEnvironment can resolve it
    // at runtime to a unique fork number, giving each worker its own SocketUtil port range.
    systemProperty(
        "test.gradleWorkerIdProperty",
        org.gradle.api.internal.tasks.testing.worker.TestWorker.WORKER_ID_SYS_PROPERTY)
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )

    if (testJvmMaxHeap != null) {
        jvmArgs("-Xmx$testJvmMaxHeap")
    }

    if (skipRandomTests) {
        exclude("**/*RandomizedPropertyTest.class", "**/*RandomizedRaftTest.class")
    }

    if (parallelTests) {
        systemProperty("junit.jupiter.execution.parallel.enabled", "true")
        systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
        systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", junitThreadCount)
    }

    useJUnitPlatform()
}

val ut by tasks.register<Test>("ut") {
    group = "verification"
    description = "Runs unit tests (Maven surefire equivalent); excludes IT* patterns"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    exclude(itPatterns)
}

val it by tasks.register<Test>("it") {
    group = "verification"
    description = "Runs integration tests (Maven failsafe equivalent); includes IT* patterns"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(ut)
    include(itPatterns)
    providers.gradleProperty("it.exclude.tests").orNull
        ?.split(",")
        ?.forEach { filter.excludeTestsMatching(it.trim()) }
}

// Lifecycle task: runs both ut and it.
tasks.named<Test>("test") {
    dependsOn(ut, it)
    testClassesDirs = files()
    classpath = files()
}

