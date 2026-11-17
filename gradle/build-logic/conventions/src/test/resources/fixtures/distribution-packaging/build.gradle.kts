import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip

plugins { base }

val camundaContents = copySpec {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from("input/camunda") {
    exclude("bin/camunda", "bin/camunda.bat")
  }
  from("input/camunda/bin") {
    into("bin")
    exclude("*.bat")
    filePermissions { unix("0755".toInt(8)) }
  }
  from("input/camunda/bin") {
    into("bin")
    include("*.bat")
    filePermissions { unix("0644".toInt(8)) }
  }
}

val optimizeScriptPaths =
  setOf(
    "optimize-startup.sh",
    "optimize-startup.bat",
    "upgrade/upgrade.sh",
    "upgrade/upgrade.bat",
  )

val optimizeContents = copySpec {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from("input/optimize") { exclude(optimizeScriptPaths) }
  from("input/optimize") {
    include(optimizeScriptPaths)
    filePermissions { unix("0755".toInt(8)) }
  }
}

tasks.register<Sync>("stageCamunda") {
  into(layout.buildDirectory.dir("staged/camunda-zeebe"))
  with(camundaContents)
}

tasks.register<Sync>("stageOptimize") {
  into(layout.buildDirectory.dir("staged/camunda-optimize"))
  with(optimizeContents)
}

tasks.register<Tar>("camundaTar") {
  compression = Compression.GZIP
  archiveFileName.set("camunda-zeebe-8.11.0-SNAPSHOT.tar.gz")
  into("camunda-zeebe-8.11.0-SNAPSHOT") { with(camundaContents) }
}

tasks.register<Zip>("camundaZip") {
  archiveFileName.set("camunda-zeebe-8.11.0-SNAPSHOT.zip")
  into("camunda-zeebe-8.11.0-SNAPSHOT") { with(camundaContents) }
}

tasks.register<Tar>("optimizeTar") {
  compression = Compression.GZIP
  archiveFileName.set("camunda-optimize-8.11.0-SNAPSHOT-production.tar.gz")
  with(optimizeContents)
}

tasks.register<Zip>("optimizeZip") {
  archiveFileName.set("camunda-optimize-8.11.0-SNAPSHOT-production.zip")
  with(optimizeContents)
}
