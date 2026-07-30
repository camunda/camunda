package buildlogic

import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

abstract class TestJarPublishingExtension @Inject constructor(objects: ObjectFactory) {
    val sourceSetName: Property<String> = objects.property(String::class.java).convention("test")
}
