/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.security.core.authz.TenantOwnedEntity;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One change of a variable.
 *
 * @param fullValue the whole value after the change, or {@code null} when the entry does not carry
 *     it. Readers must leave it {@code null} for an entry that records only the item added to the
 *     output collection of a multi-instance element or an ad-hoc sub-process: the REST mapping
 *     returns {@code fullValue} when {@code truncateValues} is false, so a non-null value would
 *     return the single item instead of the list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VariableHistoryItemEntity(
    Long variableHistoryItemKey,
    Long variableKey,
    String name,
    String value,
    @Nullable String fullValue,
    Boolean isPreview,
    Long scopeKey,
    @Nullable Long elementInstanceKey,
    @Nullable String elementId,
    Long processInstanceKey,
    @Nullable Long rootProcessInstanceKey,
    Long processDefinitionKey,
    String processDefinitionId,
    String tenantId,
    OffsetDateTime timestamp,
    VariableHistoryItemOperationType operationType,
    VariableHistoryItemVariableOperationType variableOperationType,
    VariableHistoryItemActorType actorType,
    @Nullable String actorId,
    @Nullable String agentElementId)
    implements TenantOwnedEntity {

  public VariableHistoryItemEntity {
    Objects.requireNonNull(variableHistoryItemKey, "variableHistoryItemKey");
    Objects.requireNonNull(variableKey, "variableKey");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(isPreview, "isPreview");
    Objects.requireNonNull(scopeKey, "scopeKey");
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
    Objects.requireNonNull(processDefinitionId, "processDefinitionId");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(operationType, "operationType");
    Objects.requireNonNull(variableOperationType, "variableOperationType");
    Objects.requireNonNull(actorType, "actorType");
  }

  public enum VariableHistoryItemOperationType {
    CREATE,
    UPDATE
  }

  /** Names must match the public API enum, the REST mapping resolves them with valueOf. */
  public enum VariableHistoryItemVariableOperationType {
    API,
    USER_TASK_COMPLETION,
    PROCESS_INSTANCE_CREATION,
    PROCESS_INSTANCE_MODIFICATION,
    JOB_COMPLETION,
    JOB_FAILURE,
    JOB_ERROR,
    EXECUTION_LISTENER_COMPLETION,
    MESSAGE_CORRELATION,
    SIGNAL_BROADCAST,
    CONDITIONAL_EVALUATION,
    AD_HOC_SUB_PROCESS_ACTIVATION,
    INPUT_MAPPING,
    OUTPUT_MAPPING,
    SCRIPT_RESULT,
    DECISION_RESULT,
    CALL_ACTIVITY_INPUT,
    CALL_ACTIVITY_OUTPUT,
    MULTI_INSTANCE_INPUT_ELEMENT,
    MULTI_INSTANCE_LOOP_COUNTER,
    MULTI_INSTANCE_OUTPUT_ELEMENT,
    MULTI_INSTANCE_OUTPUT_COLLECTION,
    AD_HOC_SUB_PROCESS_METADATA,
    AD_HOC_SUB_PROCESS_OUTPUT_ELEMENT,
    AD_HOC_SUB_PROCESS_OUTPUT_COLLECTION,
    UNKNOWN
  }

  public enum VariableHistoryItemActorType {
    USER,
    CLIENT,
    ENGINE,
    UNKNOWN
  }
}
