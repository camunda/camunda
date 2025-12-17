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
import java.util.function.Function;

public class SearchQueryFilterMapper {

  public static IncidentFilter toIncidentFilter(
      final List<String> processDefinitionId,
      final List<IncidentErrorType> errorType,
      final List<String> errorMessage,
      final List<String> elementId,
      final OffsetDateTime creationTimeFrom,
      final OffsetDateTime creationTimeTo,
      final List<IncidentState> state,
      final List<String> tenantId,
      final List<Long> incidentKey,
      final List<Long> processDefinitionKey,
      final List<Long> processInstanceKey,
      final List<Long> elementInstanceKey,
      final List<Long> jobKey) {
    final var builder = FilterBuilders.incident();

    ofNullable(createListFilterOperation(processDefinitionId))
        .ifPresent(builder::processDefinitionIdOperations);
    ofNullable(createEnumListFilterOperation(errorType)).ifPresent(builder::errorTypeOperations);
    ofNullable(createListFilterOperation(errorMessage)).ifPresent(builder::errorMessageOperations);
    ofNullable(createListFilterOperation(elementId)).ifPresent(builder::flowNodeIdOperations);
    ofNullable(createDateTimeFilterOperation(creationTimeFrom, creationTimeTo))
        .ifPresent(builder::creationTimeOperations);
    ofNullable(createEnumListFilterOperation(state)).ifPresent(builder::stateOperations);
    ofNullable(createListFilterOperation(tenantId)).ifPresent(builder::tenantIdOperations);
    ofNullable(createListFilterOperation(incidentKey)).ifPresent(builder::incidentKeyOperations);
    ofNullable(createListFilterOperation(processDefinitionKey))
        .ifPresent(builder::processDefinitionKeyOperations);
    ofNullable(createListFilterOperation(processInstanceKey))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(createListFilterOperation(elementInstanceKey))
        .ifPresent(builder::flowNodeInstanceKeyOperations);
    ofNullable(createListFilterOperation(jobKey)).ifPresent(builder::jobKeyOperations);

    return builder.build();
  }

  private static <T> List<Operation<T>> createListFilterOperation(final List<T> values) {
    return createListFilterOperation(values, Function.identity());
  }

  private static <T, F> List<Operation<F>> createListFilterOperation(
      final List<T> values, final Function<T, F> valueMapper) {
    if (values == null || values.isEmpty()) {
      return null;
    }

    final var mappedValues = values.stream().map(valueMapper).toList();
    if (mappedValues.size() == 1) {
      return List.of(Operation.eq(mappedValues.getFirst()));
    }

    return List.of(Operation.in(mappedValues));
  }

  private static <E extends Enum<E>> List<Operation<String>> createEnumListFilterOperation(
      final List<E> values) {
    return createListFilterOperation(values, Enum::name);
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
