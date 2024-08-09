/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IncidentEntity(
    Long key,
    Long processDefinitionKey,
    Long processInstanceKey,
    String type,
    String flowNodeId,
    String flowNodeInstanceId,
    String creationTime,
    String state,
    Long jobKey,
    String tenantId,
    Boolean hasActiveOperation,
    OperationEntity lastOperation,
    ProcessInstanceReference rootCauseInstance,
    DecisionInstanceReference rootCauseDecision) {}
