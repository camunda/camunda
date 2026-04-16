/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToKeyOperations;
import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static java.util.Optional.ofNullable;

import io.camunda.search.filter.BatchOperationItemFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class BatchOperationItemFilterMapper {

  private BatchOperationItemFilterMapper() {}

  public static Either<List<String>, BatchOperationItemFilter> toBatchOperationItemFilter(
      final io.camunda.gateway.protocol.model.BatchOperationItemFilter filter) {
    final var builder = FilterBuilders.batchOperationItem();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      ofNullable(filter.getBatchOperationKey())
          .map(mapToOperations(String.class))
          .ifPresent(builder::batchOperationKeyOperations);
      ofNullable(filter.getItemKey())
          .map(mapToKeyOperations("itemKey", validationErrors))
          .ifPresent(builder::itemKeyOperations);
      ofNullable(filter.getProcessInstanceKey())
          .map(mapToKeyOperations("processInstanceKey", validationErrors))
          .ifPresent(builder::processInstanceKeyOperations);
      ofNullable(filter.getState())
          .map(mapToOperations(String.class))
          .ifPresent(builder::stateOperations);
      ofNullable(filter.getOperationType())
          .map(mapToOperations(String.class))
          .ifPresent(builder::operationTypeOperations);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
