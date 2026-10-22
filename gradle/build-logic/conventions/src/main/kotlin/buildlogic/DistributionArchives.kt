package buildlogic

import org.gradle.api.tasks.bundling.AbstractArchiveTask

/**
 * Makes the archive bytes stable across runs, so tasks that consume the archive, such as a
 * container image build, stay up to date when nothing changed.
 */
fun AbstractArchiveTask.reproducibleArchive() {
  isReproducibleFileOrder = true
  isPreserveFileTimestamps = false
  // Archive tasks are not cacheable by default.
  outputs.cacheIf { true }
}
