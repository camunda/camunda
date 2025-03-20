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
    String bpmnProcessId,
    Long processInstanceKey,
    ErrorType errorType,
    String errorMessage,
    String flowNodeId,
    Long flowNodeInstanceKey,
    String creationTime,
    IncidentState state,
    Long jobKey,
    String treePath,
    String tenantId) {

  public enum IncidentState {
    ACTIVE,
    MIGRATED,
    RESOLVED,
    PENDING
  }

  public enum ErrorType {
    UNSPECIFIED,
    UNKNOWN,
    IO_MAPPING_ERROR,
    JOB_NO_RETRIES,
    EXECUTION_LISTENER_NO_RETRIES,
    CONDITION_ERROR,
    EXTRACT_VALUE_ERROR,
    CALLED_ELEMENT_ERROR,
    UNHANDLED_ERROR_EVENT,
    MESSAGE_SIZE_EXCEEDED,
    CALLED_DECISION_ERROR,
    DECISION_EVALUATION_ERROR,
    FORM_NOT_FOUND,
    RESOURCE_NOT_FOUND
  }
}
