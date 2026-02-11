/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/*
 * Convention plugin for modules that generate Java client models from OpenAPI specifications
 */

plugins {
    id("buildlogic.java-conventions")
    id("org.openapi.generator")
}

// Default OpenAPI spec location (can be overridden in specific modules)
val openapiDir = "${project.rootDir}/zeebe/gateway-protocol/src/main/proto/v2"

openApiGenerate {
    generatorName.set("java")
    inputSpec.set("${openapiDir}/rest-api.yaml")
    outputDir.set("${project.layout.buildDirectory.get()}/generated/openapi")
    // modelPackage must be set in the individual module's build.gradle.kts

    // Disable tests and documentation generation
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
    generateApiTests.set(false)
    generateApiDocumentation.set(false)

    // Use globalProperties to control what gets generated:
    // - models: "" (empty string) means generate all models
    // - apis: "false" means generate no APIs
    // - supportingFiles: "false" means generate no supporting files
    globalProperties.set(mapOf(
        "models" to "",
        "apis" to "false",
        "supportingFiles" to "false"
    ))

    // Common type mappings used across client modules
    typeMappings.set(mapOf(
        // map OffsetDateTime to String to avoid timezone issues and do validation manually
        "OffsetDateTime" to "String",
        // map complex String schemas to String
        "ProcessInstanceModificationActivateInstructionAncestorElementInstanceKey" to "String",
        "ResourceKey" to "String",
        // map all *Key types to String
        "ElementInstanceKey" to "String",
        "AuditLogKey" to "String",
        "AuthorizationKey" to "String",
        "DecisionDefinitionKey" to "String",
        "DecisionInstanceKey" to "String",
        "DecisionRequirementsKey" to "String",
        "DeploymentKey" to "String",
        "FormKey" to "String",
        "GroupKey" to "String",
        "IncidentKey" to "String",
        "JobKey" to "String",
        "MappingRuleKey" to "String",
        "MessageSubscriptionKey" to "String",
        "ProcessDefinitionKey" to "String",
        "ProcessInstanceKey" to "String",
        "RoleKey" to "String",
        "TenantKey" to "String",
        "UserKey" to "String",
        "UserTaskKey" to "String",
        "VariableKey" to "String",
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

    skipValidateSpec.set(true)

    // Import mappings to avoid unnecessary imports for type-mapped classes
    importMappings.set(mapOf(
        "ElementInstanceKey" to "java.lang.String"
    ))

    // Configuration options
    configOptions.set(mapOf(
        "additionalModelTypeAnnotations" to "@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)",
        "documentationProvider" to "none",
        "enumUnknownDefaultCase" to "true",
        "library" to "google-api-client",
        "java8" to "true",
        "openApiNullable" to "false",
        "serializationLibrary" to "jackson",
        "useReflectionEqualsHashCode" to "false",
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
