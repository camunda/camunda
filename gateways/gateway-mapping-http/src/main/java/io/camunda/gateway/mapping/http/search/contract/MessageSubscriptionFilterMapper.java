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
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MessageSubscriptionFilterMapper {

  private MessageSubscriptionFilterMapper() {}

  public static Either<List<String>, MessageSubscriptionFilter> toMessageSubscriptionFilter(
      final io.camunda.gateway.protocol.model.MessageSubscriptionFilter filter) {
    final var builder = FilterBuilders.messageSubscription();
    final List<String> validationErrors = new ArrayList<>();
    ofNullable(filter.getMessageSubscriptionKey())
        .map(mapToKeyOperations("messageSubscriptionKey", validationErrors))
        .ifPresent(builder::messageSubscriptionKeyOperations);
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
    ofNullable(filter.getMessageSubscriptionState())
        .map(mapToOperations(String.class))
        .ifPresent(builder::messageSubscriptionStateOperations);
    ofNullable(filter.getLastUpdatedDate())
        .map(mapToOffsetDateTimeOperations("lastUpdatedDate", validationErrors))
        .ifPresent(builder::dateTimeOperations);
    ofNullable(filter.getMessageName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::messageNameOperations);
    ofNullable(filter.getCorrelationKey())
        .map(mapToOperations(String.class))
        .ifPresent(builder::correlationKeyOperations);
    ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
