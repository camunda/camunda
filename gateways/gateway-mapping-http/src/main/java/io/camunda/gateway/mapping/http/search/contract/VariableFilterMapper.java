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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableFilterStrictContract;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.VariableFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class VariableFilterMapper {

  private VariableFilterMapper() {}

  public static Either<List<String>, VariableFilter> toVariableFilter(
      @Nullable final GeneratedVariableFilterStrictContract filter) {
    if (filter == null) {
      return Either.right(FilterBuilders.variable().build());
    }
    final var builder = FilterBuilders.variable();
    final List<String> validationErrors = new ArrayList<>();
    ofNullable(filter.processInstanceKey())
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    ofNullable(filter.scopeKey())
        .map(mapToKeyOperations("scopeKey", validationErrors))
        .ifPresent(builder::scopeKeyOperations);
    ofNullable(filter.variableKey())
        .map(mapToKeyOperations("variableKey", validationErrors))
        .ifPresent(builder::variableKeyOperations);
    ofNullable(filter.tenantId()).ifPresent(builder::tenantIds);
    ofNullable(filter.isTruncated()).ifPresent(builder::isTruncated);
    ofNullable(filter.name()).map(mapToOperations(String.class)).ifPresent(builder::nameOperations);
    ofNullable(filter.value())
        .map(mapToOperations(String.class))
        .ifPresent(builder::valueOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
