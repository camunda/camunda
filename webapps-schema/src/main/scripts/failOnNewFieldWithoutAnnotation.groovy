/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import japicmp.model.JApiChangeStatus
import japicmp.model.*

def requiredAnnotation = "io.camunda.webapps.schema.entities.SinceVersion"

jApiClasses.each { jApiClass ->
    // Only consider classes that were modified
    if (jApiClass.getFullyQualifiedName().startsWith("io.camunda.webapps.schema.entities") &&
            jApiClass.getChangeStatus() == JApiChangeStatus.MODIFIED &&
            jApiClass.getClassType().getOldTypeOptional().get() != JApiClassType.ClassType.ENUM ) {

        jApiClass.getFields().each { field ->
            // Check only fields that are newly added within the modified class
            if (field.getChangeStatus() == JApiChangeStatus.NEW) {
                if (!field.getAnnotations().any { it.getFullyQualifiedName() == requiredAnnotation }) {
                    throw new Exception(
                            "New field added without required annotation @SinceVersion: " +
                                    "${jApiClass.getFullyQualifiedName()}.${field.getName()}"
                    )
                }
            }
        }
    }
}

return jApiClasses

