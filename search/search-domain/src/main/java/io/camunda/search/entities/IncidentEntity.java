/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IncidentEntity(
    Long incidentKey,
    Long processDefinitionKey,
    String processDefinitionId,
    Long processInstanceKey,
    @Nullable Long rootProcessInstanceKey,
    @Nullable ErrorType errorType,
    String errorMessage,
    String flowNodeId,
    Long flowNodeInstanceKey,
    OffsetDateTime creationTime,
    @Nullable IncidentState state,
    @Nullable Long jobKey,
    String tenantId,
    @Nullable String updatedBy,
    @Nullable OffsetDateTime updatedAt)
    implements TenantOwnedEntity {

  public IncidentEntity {
    Objects.requireNonNull(incidentKey, "incidentKey");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(errorMessage, "errorMessage");
    Objects.requireNonNull(flowNodeId, "flowNodeId");
    Objects.requireNonNull(flowNodeInstanceKey, "flowNodeInstanceKey");
    Objects.requireNonNull(creationTime, "creationTime");
    Objects.requireNonNull(tenantId, "tenantId");
  }

  public IncidentEntity(
      final Long incidentKey,
      final Long processDefinitionKey,
      final String processDefinitionId,
      final Long processInstanceKey,
      final @Nullable Long rootProcessInstanceKey,
      final @Nullable ErrorType errorType,
      final String errorMessage,
      final String flowNodeId,
      final Long flowNodeInstanceKey,
      final OffsetDateTime creationTime,
      final @Nullable IncidentState state,
      final @Nullable Long jobKey,
      final String tenantId) {
    this(
        incidentKey,
        processDefinitionKey,
        processDefinitionId,
        processInstanceKey,
        rootProcessInstanceKey,
        errorType,
        errorMessage,
        flowNodeId,
        flowNodeInstanceKey,
        creationTime,
        state,
        jobKey,
        tenantId,
        null,
        null);
  }

  public IncidentEntity withUpdateMetadata(
      final @Nullable String newUpdatedBy, final @Nullable OffsetDateTime newUpdatedAt) {
    return new IncidentEntity(
        incidentKey,
        processDefinitionKey,
        processDefinitionId,
        processInstanceKey,
        rootProcessInstanceKey,
        errorType,
        errorMessage,
        flowNodeId,
        flowNodeInstanceKey,
        creationTime,
        state,
        jobKey,
        tenantId,
        newUpdatedBy,
        newUpdatedAt);
  }

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
    TASK_LISTENER_NO_RETRIES,
    AD_HOC_SUB_PROCESS_NO_RETRIES,
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
