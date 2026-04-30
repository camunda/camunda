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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessInstanceEntity(
    Long processInstanceKey,
    @Nullable Long rootProcessInstanceKey,
    // @Nullable due to the onlyKeys(true) source projection in
    // ProcessInstanceItemProvider#fetchItemPage, which asks ES to return only `key` and
    // `rootProcessInstanceKey` and leaves these identity fields null on that read path.
    // Tracked: https://github.com/camunda/camunda/issues/51999.
    @Nullable String processDefinitionId,
    @Nullable String processDefinitionName,
    // see processDefinitionId / #51999.
    @Nullable Integer processDefinitionVersion,
    @Nullable String processDefinitionVersionTag,
    // see processDefinitionId / #51999.
    @Nullable Long processDefinitionKey,
    @Nullable Long parentProcessInstanceKey,
    @Nullable Long parentFlowNodeInstanceKey,
    // only written on ELEMENT_ACTIVATING; absent on docs first created by a later intent (e.g.
    // batch-op listview entries or incident updates before the activating record is processed).
    @Nullable OffsetDateTime startDate,
    @Nullable OffsetDateTime endDate,
    // not always set on doc creation; the activating intent is what populates state, and other
    // intents (batch-op chunk, incident updates) can create or update the doc before then.
    @Nullable ProcessInstanceState state,
    // not set by the primary handler; populated asynchronously by IncidentUpdateTask.
    @Nullable Boolean hasIncident,
    String tenantId,
    @Nullable String treePath,
    Set<String> tags,
    @Nullable String businessId)
    implements TenantOwnedEntity {

  public ProcessInstanceEntity {
    Objects.requireNonNull(processInstanceKey, "processInstanceKey");
    Objects.requireNonNull(tenantId, "tenantId");
    // Mutable collections are required: MyBatis hydrates collection-mapped fields (e.g. from a
    // <collection> result map or a LEFT JOIN) by calling .add() on the existing instance.
    // Immutable defaults (e.g. Set.of()) would cause UnsupportedOperationException at runtime.
    tags = tags != null ? tags : new HashSet<>();
  }

  public ProcessInstanceEntity(
      final Long processInstanceKey,
      final Long rootProcessInstanceKey,
      final String processDefinitionId,
      final String processDefinitionName,
      final Integer processDefinitionVersion,
      final String processDefinitionVersionTag,
      final Long processDefinitionKey,
      final Long parentProcessInstanceKey,
      final Long parentFlowNodeInstanceKey,
      final OffsetDateTime startDate,
      final OffsetDateTime endDate,
      final ProcessInstanceState state,
      final Boolean hasIncident,
      final String tenantId,
      final String treePath,
      final String businessId) {

    this(
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionId,
        processDefinitionName,
        processDefinitionVersion,
        processDefinitionVersionTag,
        processDefinitionKey,
        parentProcessInstanceKey,
        parentFlowNodeInstanceKey,
        startDate,
        endDate,
        state,
        hasIncident,
        tenantId,
        treePath,
        new HashSet<>(),
        businessId);
  }

  public enum ProcessInstanceState {
    ACTIVE,
    COMPLETED,
    SUSPENDED,
    CANCELED;

    public static ProcessInstanceState fromValue(final String value) {
      for (final ProcessInstanceState state : ProcessInstanceState.values()) {
        if (state.name().equals(value)) {
          return state;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }
}
