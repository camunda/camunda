package io.camunda.gradle.pom

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import java.io.File

class SettingsPomResolverPlugin : Plugin<Settings> {
  override fun apply(settings: Settings) {
    settings.extensions.create("settingsPomResolver", SettingsPomResolver::class.java, settings)
  }
}

open class SettingsPomResolver(private val settings: Settings) {
  private fun readPom(relativePath: String): String = File(settings.rootDir, relativePath).readText()

  fun pom(relativePath: String): PomResolver = PomResolver(readPom(relativePath))

  fun propertiesForPom(relativePath: String): Map<String, String> = pom(relativePath).properties()

  fun projectVersionForPom(relativePath: String): String =
    pom(relativePath).projectVersion()
      ?: error("Missing Maven project version from $relativePath")

  fun resolveProperty(propertyName: String, vararg propertyMaps: Map<String, String>): String =
    PomResolver("<project />").resolveProperty(propertyName, *propertyMaps)

  fun resolveProperty(propertyName: String, propertyMaps: List<Map<String, String>>): String =
    resolveProperty(propertyName, *propertyMaps.toTypedArray())
}
