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
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

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
    VariableOperationType variableOperationType,
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

  public enum VariableHistoryItemActorType {
    USER,
    CLIENT,
    ENGINE,
    UNKNOWN
  }
}
