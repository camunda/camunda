/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static java.util.Optional.ofNullable;

import io.camunda.gateway.mapping.http.search.contract.generated.AuditLogActorTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationFilterContract;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.filter.FilterBuilders;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class BatchOperationFilterMapper {

  private BatchOperationFilterMapper() {}

  public static BatchOperationFilter toBatchOperationFilter(
      @Nullable final BatchOperationFilterContract filter) {
    final var builder = FilterBuilders.batchOperation();
    if (filter != null) {
      ofNullable(filter.batchOperationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationKeyOperations);
      ofNullable(filter.state())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.operationType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::operationTypeOperations);
      ofNullable(filter.actorType())
          .map(AuditLogActorTypeEnum::getValue)
          .map(String::toUpperCase)
          .ifPresent(builder::actorTypes);
      ofNullable(filter.actorId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::actorIdOperations);
    }
    return builder.build();
  }
}
