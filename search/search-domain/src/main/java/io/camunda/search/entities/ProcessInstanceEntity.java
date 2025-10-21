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
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessInstanceEntity(
    Long processInstanceKey,
    String processDefinitionId,
    String processDefinitionName,
    Integer processDefinitionVersion,
    String processDefinitionVersionTag,
    Long processDefinitionKey,
    Long parentProcessInstanceKey,
    Long parentFlowNodeInstanceKey,
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    ProcessInstanceState state,
    Boolean hasIncident,
    String tenantId,
    String treePath,
    Set<String> tags)
    implements TenantOwnedEntity {

  public ProcessInstanceEntity(
      final Long processInstanceKey,
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
      final String treePath) {

    this(
        processInstanceKey,
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
        new HashSet<>());
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
