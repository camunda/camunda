/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToKeyOperations;
import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOffsetDateTimeOperations;
import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static java.util.Optional.ofNullable;

import io.camunda.gateway.mapping.http.search.contract.generated.IncidentFilterContract;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class IncidentFilterMapper {

  private IncidentFilterMapper() {}

  public static Either<List<String>, IncidentFilter> toIncidentFilter(
      @Nullable final IncidentFilterContract filter) {
    final var builder = FilterBuilders.incident();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      ofNullable(filter.incidentKey())
          .map(mapToKeyOperations("incidentKey", validationErrors))
          .ifPresent(builder::incidentKeyOperations);
      ofNullable(filter.processDefinitionKey())
          .map(mapToKeyOperations("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeyOperations);
      ofNullable(filter.processDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.processInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
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
          .map(mapToKeyOperations("elementInstanceKey", validationErrors))
          .ifPresent(builder::flowNodeInstanceKeyOperations);
      ofNullable(filter.creationTime())
          .map(mapToOffsetDateTimeOperations("creationTime", validationErrors))
          .ifPresent(builder::creationTimeOperations);
      ofNullable(filter.state())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.jobKey())
          .map(mapToKeyOperations("jobKey", validationErrors))
          .ifPresent(builder::jobKeyOperations);
      ofNullable(filter.tenantId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::tenantIdOperations);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
