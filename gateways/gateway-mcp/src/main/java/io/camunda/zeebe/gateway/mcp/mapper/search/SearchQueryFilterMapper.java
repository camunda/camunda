/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.mapper.search;

import static io.camunda.zeebe.gateway.mcp.util.AdvancedSearchFilterUtil.mapToOperations;
import static java.util.Optional.ofNullable;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.IncidentFilter;
import java.time.OffsetDateTime;

public class SearchQueryFilterMapper {

  public static IncidentFilter toIncidentFilter(
      final io.camunda.zeebe.gateway.mcp.model.IncidentFilter filter) {
    final var builder = FilterBuilders.incident();

    if (filter == null) {
      return builder.build();
    }

    ofNullable(filter.incidentKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::incidentKeyOperations);
    ofNullable(filter.processDefinitionKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processDefinitionKeyOperations);
    ofNullable(filter.processDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    ofNullable(filter.processInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.errorType())
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorTypeOperations);
    ofNullable(filter.errorMessage())
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    ofNullable(filter.elementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    ofNullable(filter.elementInstanceKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::flowNodeInstanceKeyOperations);
    ofNullable(filter.creationTime())
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::creationTimeOperations);
    ofNullable(filter.state())
        .map(mapToOperations(String.class))
        .ifPresent(builder::stateOperations);
    ofNullable(filter.jobKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::jobKeyOperations);
    ofNullable(filter.tenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);

    return builder.build();
  }
}
