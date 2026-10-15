package io.camunda.gradle.flags

import org.gradle.api.provider.Provider

/**
 * Reads a Gradle property as a boolean flag.
 *
 * An empty value means "enabled" so that `-Pflag` works without a value; any other value is parsed
 * with [String.toBoolean].
 */
fun Provider<String>.asEnabledFlag(): Provider<Boolean> = map { value ->
  value.isEmpty() || value.toBoolean()
}
