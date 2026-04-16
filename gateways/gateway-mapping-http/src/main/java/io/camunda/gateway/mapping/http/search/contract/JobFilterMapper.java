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

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class JobFilterMapper {

  private JobFilterMapper() {}

  public static Either<List<String>, JobFilter> toJobFilter(
      final io.camunda.gateway.protocol.model.JobFilter filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.job().build());
    }
    final var builder = FilterBuilders.job();
    final List<String> validationErrors = new ArrayList<>();
    ofNullable(filter.getJobKey())
        .map(mapToKeyOperations("jobKey", validationErrors))
        .ifPresent(builder::jobKeyOperations);
    ofNullable(filter.getProcessDefinitionKey())
        .map(mapToKeyOperations("processDefinitionKey", validationErrors))
        .ifPresent(builder::processDefinitionKeyOperations);
    ofNullable(filter.getProcessDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    ofNullable(filter.getProcessInstanceKey())
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.getElementInstanceKey())
        .map(mapToKeyOperations("elementInstanceKey", validationErrors))
        .ifPresent(builder::elementInstanceKeyOperations);
    ofNullable(filter.getElementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::elementIdOperations);
    ofNullable(filter.getState())
        .map(mapToOperations(String.class))
        .ifPresent(builder::stateOperations);
    ofNullable(filter.getType())
        .map(mapToOperations(String.class))
        .ifPresent(builder::typeOperations);
    ofNullable(filter.getWorker())
        .map(mapToOperations(String.class))
        .ifPresent(builder::workerOperations);
    ofNullable(filter.getRetries())
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::retriesOperations);
    ofNullable(filter.getErrorMessage())
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    ofNullable(filter.getErrorCode())
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorCodeOperations);
    ofNullable(filter.getHasFailedWithRetriesLeft()).ifPresent(builder::hasFailedWithRetriesLeft);
    ofNullable(filter.getIsDenied()).ifPresent(builder::isDenied);
    ofNullable(filter.getDeniedReason())
        .map(mapToOperations(String.class))
        .ifPresent(builder::deniedReasonOperations);
    ofNullable(filter.getKind())
        .map(mapToOperations(String.class))
        .ifPresent(builder::kindOperations);
    ofNullable(filter.getListenerEventType())
        .map(mapToOperations(String.class))
        .ifPresent(builder::listenerEventTypeOperations);
    ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    ofNullable(filter.getDeadline())
        .map(mapToOffsetDateTimeOperations("deadline", validationErrors))
        .ifPresent(builder::deadlineOperations);
    ofNullable(filter.getEndTime())
        .map(mapToOffsetDateTimeOperations("endTime", validationErrors))
        .ifPresent(builder::endTimeOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
