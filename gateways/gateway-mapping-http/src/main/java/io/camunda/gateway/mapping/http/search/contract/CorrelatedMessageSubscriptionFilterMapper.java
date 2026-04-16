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

import io.camunda.search.filter.CorrelatedMessageSubscriptionFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CorrelatedMessageSubscriptionFilterMapper {

  private CorrelatedMessageSubscriptionFilterMapper() {}

  public static Either<List<String>, CorrelatedMessageSubscriptionFilter>
      toCorrelatedMessageSubscriptionFilter(
          final io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionFilter filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.correlatedMessageSubscription().build());
    }
    final var builder = FilterBuilders.correlatedMessageSubscription();
    final List<String> validationErrors = new ArrayList<>();
    ofNullable(filter.getSubscriptionKey())
        .map(mapToKeyOperations("subscriptionKey", validationErrors))
        .ifPresent(builder::subscriptionKeyOperations);
    ofNullable(filter.getProcessDefinitionKey())
        .map(mapToKeyOperations("processDefinitionKey", validationErrors))
        .ifPresent(builder::processDefinitionKeyOperations);
    ofNullable(filter.getProcessDefinitionId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::processDefinitionIdOperations);
    ofNullable(filter.getProcessInstanceKey())
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.getElementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    ofNullable(filter.getElementInstanceKey())
        .map(mapToKeyOperations("elementInstanceKey", validationErrors))
        .ifPresent(builder::flowNodeInstanceKeyOperations);
    ofNullable(filter.getMessageKey())
        .map(mapToOperations(Long.class))
        .ifPresent(builder::messageKeyOperations);
    ofNullable(filter.getMessageName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::messageNameOperations);
    ofNullable(filter.getCorrelationKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::correlationKeyOperations);
    ofNullable(filter.getCorrelationTime())
        .map(mapToOffsetDateTimeOperations("correlationTime", validationErrors))
        .ifPresent(builder::correlationTimeOperations);
    ofNullable(filter.getPartitionId())
        .map(mapToOperations(Integer.class))
        .ifPresent(builder::partitionIdOperations);
    ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
