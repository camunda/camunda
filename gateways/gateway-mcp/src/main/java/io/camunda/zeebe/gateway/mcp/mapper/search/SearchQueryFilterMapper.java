/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.mapper.search;

import static java.util.Optional.ofNullable;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.Operation;
import io.camunda.zeebe.gateway.mcp.model.IncidentErrorType;
import io.camunda.zeebe.gateway.mcp.model.IncidentState;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class SearchQueryFilterMapper {

  public static IncidentFilter toIncidentFilter(
      final String processDefinitionId,
      final IncidentErrorType errorType,
      final String errorMessage,
      final String elementId,
      final OffsetDateTime creationTimeFrom,
      final OffsetDateTime creationTimeTo,
      final IncidentState state,
      final String tenantId,
      final Long incidentKey,
      final Long processDefinitionKey,
      final Long processInstanceKey,
      final Long elementInstanceKey,
      final Long jobKey) {
    final var builder = FilterBuilders.incident();

    ofNullable(createEqualOperation(processDefinitionId))
        .ifPresent(builder::processDefinitionIdOperations);
    ofNullable(createEnumEqualOperation(errorType)).ifPresent(builder::errorTypeOperations);
    ofNullable(createEqualOperation(errorMessage)).ifPresent(builder::errorMessageOperations);
    ofNullable(createEqualOperation(elementId)).ifPresent(builder::flowNodeIdOperations);
    ofNullable(createDateTimeFilterOperation(creationTimeFrom, creationTimeTo))
        .ifPresent(builder::creationTimeOperations);
    ofNullable(createEnumEqualOperation(state)).ifPresent(builder::stateOperations);
    ofNullable(createEqualOperation(tenantId)).ifPresent(builder::tenantIdOperations);
    ofNullable(createEqualOperation(incidentKey)).ifPresent(builder::incidentKeyOperations);
    ofNullable(createEqualOperation(processDefinitionKey))
        .ifPresent(builder::processDefinitionKeyOperations);
    ofNullable(createEqualOperation(processInstanceKey))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(createEqualOperation(elementInstanceKey))
        .ifPresent(builder::flowNodeInstanceKeyOperations);
    ofNullable(createEqualOperation(jobKey)).ifPresent(builder::jobKeyOperations);

    return builder.build();
  }

  private static <T> List<Operation<T>> createEqualOperation(final T value) {
    if (value == null) {
      return null;
    }

    return List.of(Operation.eq(value));
  }

  private static <E extends Enum<E>> List<Operation<String>> createEnumEqualOperation(
      final E value) {
    if (value == null) {
      return null;
    }

    return List.of(Operation.eq(value.name()));
  }

  private static List<Operation<OffsetDateTime>> createDateTimeFilterOperation(
      final OffsetDateTime from, final OffsetDateTime to) {
    final List<Operation<OffsetDateTime>> operations = new ArrayList<>();
    if (from != null) {
      operations.add(Operation.gte(from));
    }

    if (to != null) {
      operations.add(Operation.lt(to));
    }

    return operations.isEmpty() ? null : operations;
  }
}
