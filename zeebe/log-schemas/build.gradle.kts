plugins { id("buildlogic.sbe-conventions") }

sbe {
  schemaFiles.from(
    layout.projectDirectory.file("src/main/resources/journal-schema.xml"),
    layout.projectDirectory.file("src/main/resources/raft-entry-schema.xml"),
  )
  // SBE also emits a package-info.java for each schema package. Both packages are shared with
  // hand-written classes in zeebe-journal and zeebe-atomix-cluster, which have their own.
  generatedFilesToDelete.add("io/camunda/zeebe/journal/file/package-info.java")
  generatedFilesToDelete.add("io/atomix/raft/storage/serializer/package-info.java")
}

dependencies { api(libs.org.agrona.agrona) }

description = "Zeebe Log Schemas"
