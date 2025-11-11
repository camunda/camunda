/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditLogEntity(
    String operationKey,
    long entityKey, // TODO long or string?
    Short entityType,
    int entityVersion,
    Short operationType,
    Long batchOperationKey,
    Long timestamp,
    String actorId,
    String actorType,
    String tenantId,
    String operationState,
    String operationNote,
    Short operationCategory,
    Object operationDetails,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long elementInstanceKey,
    Long userTaskKey,
    Long decisionRequirementsKey,
    Long decisionKey) {}
