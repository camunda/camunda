plugins { `java-library` }

dependencies {
  runtimeOnly("io.netty:netty-tcnative-boringssl-static::linux-x86_64")
  runtimeOnly("io.netty:netty-tcnative-boringssl-static::linux-aarch_64")
  runtimeOnly("io.netty:netty-tcnative-boringssl-static::osx-x86_64")
  runtimeOnly("io.netty:netty-tcnative-boringssl-static::osx-aarch_64")
  runtimeOnly("io.netty:netty-tcnative-boringssl-static::windows-x86_64")
}
