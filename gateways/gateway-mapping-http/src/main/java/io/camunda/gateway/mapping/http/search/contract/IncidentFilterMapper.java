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

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class IncidentFilterMapper {

  private IncidentFilterMapper() {}

  public static Either<List<String>, IncidentFilter> toIncidentFilter(
      final io.camunda.gateway.protocol.model.IncidentFilter filter) {
    final var builder = FilterBuilders.incident();
    final List<String> validationErrors = new ArrayList<>();
    filter
        .getIncidentKey()
        .map(mapToKeyOperations("incidentKey", validationErrors))
        .ifPresent(builder::incidentKeyOperations);
    filter
        .getProcessDefinitionKey()
        .map(mapToKeyOperations("processDefinitionKey", validationErrors))
        .ifPresent(builder::processDefinitionKeyOperations);
    filter
        .getProcessDefinitionId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    filter
        .getProcessInstanceKey()
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    filter
        .getErrorType()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorTypeOperations);
    filter
        .getErrorMessage()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    filter
        .getElementId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    filter
        .getElementInstanceKey()
        .map(mapToKeyOperations("elementInstanceKey", validationErrors))
        .ifPresent(builder::flowNodeInstanceKeyOperations);
    filter
        .getCreationTime()
        .map(mapToOffsetDateTimeOperations("creationTime", validationErrors))
        .ifPresent(builder::creationTimeOperations);
    filter.getState().map(mapToOperations(String.class)).ifPresent(builder::stateOperations);
    filter
        .getJobKey()
        .map(mapToKeyOperations("jobKey", validationErrors))
        .ifPresent(builder::jobKeyOperations);
    filter.getTenantId().map(mapToOperations(String.class)).ifPresent(builder::tenantIdOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
