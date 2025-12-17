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
import io.camunda.zeebe.gateway.mcp.model.IncidentSearchFilter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class SearchQueryFilterMapper {

  public static IncidentFilter toIncidentFilter(final IncidentSearchFilter filter) {
    final var builder = FilterBuilders.incident();

    if (filter != null) {
      ofNullable(filter.processDefinitionId())
          .map(SearchQueryFilterMapper::createEqualOperation)
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.errorType())
          .map(SearchQueryFilterMapper::createEnumEqualOperation)
          .ifPresent(builder::errorTypeOperations);
      ofNullable(filter.elementId())
          .map(SearchQueryFilterMapper::createEqualOperation)
          .ifPresent(builder::flowNodeIdOperations);
      ofNullable(createDateTimeFilterOperation(filter.creationTimeFrom(), filter.creationTimeTo()))
          .ifPresent(builder::creationTimeOperations);
      ofNullable(filter.state())
          .map(SearchQueryFilterMapper::createEnumEqualOperation)
          .ifPresent(builder::stateOperations);
      ofNullable(filter.processDefinitionKey())
          .map(SearchQueryFilterMapper::createEqualOperation)
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.processInstanceKey())
          .map(SearchQueryFilterMapper::createEqualOperation)
          .ifPresent(builder::processInstanceKeyOperations);
    }
    return builder.build();
  }

  private static <T> List<Operation<T>> createEqualOperation(final T value) {
    return List.of(Operation.eq(value));
  }

  private static <E extends Enum<E>> List<Operation<String>> createEnumEqualOperation(
      final E value) {
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
