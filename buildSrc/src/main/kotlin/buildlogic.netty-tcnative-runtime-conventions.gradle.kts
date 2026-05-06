import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    `java-library`
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val nettyTcnativeVersion =
    versionCatalog.findVersion("io-netty-netty-tcnative-boringssl-static").get().requiredVersion

dependencies {
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:${nettyTcnativeVersion}:linux-x86_64")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:${nettyTcnativeVersion}:linux-aarch_64")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:${nettyTcnativeVersion}:osx-x86_64")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:${nettyTcnativeVersion}:osx-aarch_64")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:${nettyTcnativeVersion}:windows-x86_64")
}
