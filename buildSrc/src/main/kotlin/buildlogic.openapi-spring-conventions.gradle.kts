/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * Convention plugin for modules that generate Spring server models from OpenAPI specifications
 */

plugins {
    id("buildlogic.java-conventions")
    id("org.openapi.generator")
}

// Default OpenAPI spec location (can be overridden in specific modules)
val openapiDir = "${project.rootDir}/zeebe/gateway-protocol/src/main/proto/v2"

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("${openapiDir}/rest-api.yaml")
    outputDir.set("${project.layout.buildDirectory.get()}/generated/openapi")

    // Prevent API generation by constraining to empty list
    apiFilesConstrainedTo.set(listOf())

    // Common type mappings used across Spring modules
    typeMappings.set(mapOf(
        // map OffsetDateTime to String to avoid timezone issues and do validation manually
        "OffsetDateTime" to "String",
        // map complex String schemas to String
        "ProcessInstanceModificationActivateInstructionAncestorElementInstanceKey" to "String",
        "ResourceKey" to "String",
        // map specific filter properties to basic filter types
        "AuditLogEntityKeyFilterProperty" to "BasicStringFilterProperty",
        "AuditLogKeyFilterProperty" to "BasicStringFilterProperty",
        "DecisionDefinitionKeyFilterProperty" to "BasicStringFilterProperty",
        "DecisionEvaluationInstanceKeyFilterProperty" to "BasicStringFilterProperty",
        "DecisionEvaluationKeyFilterProperty" to "BasicStringFilterProperty",
        "DecisionRequirementsKeyFilterProperty" to "BasicStringFilterProperty",
        "DeploymentKeyFilterProperty" to "BasicStringFilterProperty",
        "ElementInstanceKeyFilterProperty" to "BasicStringFilterProperty",
        "FormKeyFilterProperty" to "BasicStringFilterProperty",
        "JobKeyFilterProperty" to "BasicStringFilterProperty",
        "MessageSubscriptionKeyFilterProperty" to "BasicStringFilterProperty",
        "ProcessDefinitionKeyFilterProperty" to "BasicStringFilterProperty",
        "ProcessInstanceKeyFilterProperty" to "BasicStringFilterProperty",
        "ResourceKeyFilterProperty" to "BasicStringFilterProperty",
        "ScopeKeyFilterProperty" to "BasicStringFilterProperty",
        "VariableKeyFilterProperty" to "BasicStringFilterProperty"
    ))

    // Validate spec
    skipValidateSpec.set(false)
    globalProperties.set(mapOf(
        "apis" to "false"
    ))

    // OpenAPI normalizer
    openapiNormalizer.set(mapOf(
        "REF_AS_PARENT_IN_ALLOF" to "true"
    ))

    // Configuration options
    configOptions.set(mapOf(
        "additionalModelTypeAnnotations" to "@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)",
        "serializationLibrary" to "jackson",
        "library" to "spring-boot",
        "jdk8" to "true",
        "openApiNullable" to "false",
        "useEnumCaseInsensitive" to "true",
        "sourceFolder" to "src/main/java"
    ))
}

// Add generated sources to the source set
sourceSets {
    main {
        java {
            srcDir("${project.layout.buildDirectory.get()}/generated/openapi/src/main/java")
        }
    }
}

// Make compileJava depend on openApiGenerate
tasks.named("compileJava") {
    dependsOn("openApiGenerate")
}
