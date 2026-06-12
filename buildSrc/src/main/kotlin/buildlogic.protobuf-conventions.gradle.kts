/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * Convention plugin for modules that generate code from protobuf definitions
 */

plugins {
    id("buildlogic.server-conventions")
    id("com.google.protobuf")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val protobufVersion = versionCatalog.findVersion("protobuf").get().requiredVersion
val grpcVersion = versionCatalog.findVersion("grpc").get().requiredVersion

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

// Ensure generated code is on the source path
sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated/source/proto/main/grpc"))
            srcDir(layout.buildDirectory.dir("generated/source/proto/main/java"))
        }
    }
}
