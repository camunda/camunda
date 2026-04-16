/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;

import io.camunda.gateway.protocol.model.AuditLogActorTypeEnum;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.filter.FilterBuilders;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class BatchOperationFilterMapper {

  private BatchOperationFilterMapper() {}

  public static BatchOperationFilter toBatchOperationFilter(
      final io.camunda.gateway.protocol.model.BatchOperationFilter filter) {
    final var builder = FilterBuilders.batchOperation();
    filter
        .getBatchOperationKey()
        .map(mapToOperations(String.class))
        .ifPresent(builder::batchOperationKeyOperations);
    filter.getState().map(mapToOperations(String.class)).ifPresent(builder::stateOperations);
    filter
        .getOperationType()
        .map(mapToOperations(String.class))
        .ifPresent(builder::operationTypeOperations);
    filter
        .getActorType()
        .map(AuditLogActorTypeEnum::getValue)
        .map(String::toUpperCase)
        .ifPresent(builder::actorTypes);
    filter.getActorId().map(mapToOperations(String.class)).ifPresent(builder::actorIdOperations);
    return builder.build();
  }
}
