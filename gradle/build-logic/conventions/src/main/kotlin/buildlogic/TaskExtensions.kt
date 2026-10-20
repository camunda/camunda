package buildlogic

import org.gradle.api.Task
import org.gradle.api.provider.Provider

/**
 * Skips the task when [flag] is true. Reads the flag at execution time and, unlike an inline
 * `onlyIf` in a build script, does not capture the script object, which the configuration cache
 * cannot serialize.
 */
fun Task.skipWhen(flag: Provider<Boolean>) {
  onlyIf { !flag.get() }
}
