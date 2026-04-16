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

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.VariableFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class VariableFilterMapper {

  private VariableFilterMapper() {}

  public static Either<List<String>, VariableFilter> toVariableFilter(
      final io.camunda.gateway.protocol.model.VariableFilter filter) {
    final var builder = FilterBuilders.variable();
    final List<String> validationErrors = new ArrayList<>();
    filter
        .getProcessInstanceKey()
        .map(mapToKeyOperations("processInstanceKey", validationErrors))
        .ifPresent(builder::processInstanceKeyOperations);
    filter
        .getScopeKey()
        .map(mapToKeyOperations("scopeKey", validationErrors))
        .ifPresent(builder::scopeKeyOperations);
    filter
        .getVariableKey()
        .map(mapToKeyOperations("variableKey", validationErrors))
        .ifPresent(builder::variableKeyOperations);
    filter.getTenantId().ifPresent(builder::tenantIds);
    filter.getIsTruncated().ifPresent(builder::isTruncated);
    filter.getName().map(mapToOperations(String.class)).ifPresent(builder::nameOperations);
    filter.getValue().map(mapToOperations(String.class)).ifPresent(builder::valueOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
