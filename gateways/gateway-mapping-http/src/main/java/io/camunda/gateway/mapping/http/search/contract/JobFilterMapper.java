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
    final var builder = FilterBuilders.job();
    final List<String> validationErrors = new ArrayList<>();
    filter
        .getJobKey()
        .map(mapToKeyOperations("jobKey", validationErrors))
        .ifPresent(builder::jobKeyOperations);
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
        .getElementInstanceKey()
        .map(mapToKeyOperations("elementInstanceKey", validationErrors))
        .ifPresent(builder::elementInstanceKeyOperations);
    filter
        .getElementId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::elementIdOperations);
    filter.getState().map(mapToOperations(String.class)).ifPresent(builder::stateOperations);
    filter.getType().map(mapToOperations(String.class)).ifPresent(builder::typeOperations);
    filter.getWorker().map(mapToOperations(String.class)).ifPresent(builder::workerOperations);
    filter.getRetries().map(mapToOperations(Integer.class)).ifPresent(builder::retriesOperations);
    filter
        .getErrorMessage()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    filter
        .getErrorCode()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorCodeOperations);
    filter.getHasFailedWithRetriesLeft().ifPresent(builder::hasFailedWithRetriesLeft);
    filter.getIsDenied().ifPresent(builder::isDenied);
    filter
        .getDeniedReason()
        .map(mapToOperations(String.class))
        .ifPresent(builder::deniedReasonOperations);
    filter.getKind().map(mapToOperations(String.class)).ifPresent(builder::kindOperations);
    filter
        .getListenerEventType()
        .map(mapToOperations(String.class))
        .ifPresent(builder::listenerEventTypeOperations);
    filter.getTenantId().map(mapToOperations(String.class)).ifPresent(builder::tenantIdOperations);
    filter
        .getDeadline()
        .map(mapToOffsetDateTimeOperations("deadline", validationErrors))
        .ifPresent(builder::deadlineOperations);
    filter
        .getEndTime()
        .map(mapToOffsetDateTimeOperations("endTime", validationErrors))
        .ifPresent(builder::endTimeOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
