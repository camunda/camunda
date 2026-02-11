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
    id("buildlogic.java-conventions")
    id("com.google.protobuf")
}

protobuf {
    protoc {
        // version.protobuf from parent pom: 4.33.4
        artifact = "com.google.protobuf:protoc:4.33.4"
    }
    plugins {
        create("grpc") {
            // version.grpc from parent pom: 1.78.0
            artifact = "io.grpc:protoc-gen-grpc-java:1.78.0"
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
