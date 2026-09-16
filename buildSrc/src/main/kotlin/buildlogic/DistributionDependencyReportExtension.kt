package buildlogic

import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty

abstract class DistributionDependencyReportExtension @Inject constructor(objects: ObjectFactory) {
  val excludedFilePrefixes: ListProperty<String> =
    objects.listProperty(String::class.java)
  val fileNameReplacements: MapProperty<String, String> =
    objects.mapProperty(String::class.java, String::class.java)
}
