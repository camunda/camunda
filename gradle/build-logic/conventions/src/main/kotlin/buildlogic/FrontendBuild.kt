package buildlogic

import io.camunda.gradle.flags.asEnabledFlag
import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * Whether the frontend builds are skipped. They are skipped unless -Pskip.fe.build=false asks for
 * them or a release image is requested, because tests only exercise the backend. -Pquickly skips
 * them as well.
 *
 * The requested tasks are the only input known when the build is configured, and they are part of
 * the configuration cache key, so the release image is detected by task name.
 */
fun Project.frontendBuildSkipped(): Provider<Boolean> {
  val releaseImageRequested =
    gradle.startParameter.taskRequests.any { request ->
      request.args.any { it.substringAfterLast(':') == "buildDockerImageRelease" }
    }
  return providers
    .gradleProperty("skip.fe.build")
    .orElse(providers.gradleProperty("quickly"))
    .asEnabledFlag()
    .orElse(!releaseImageRequested)
}
