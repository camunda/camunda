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

import io.camunda.gateway.protocol.model.UserTaskVariableFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.VariableFilter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class UserTaskVariableFilterMapper {

  private UserTaskVariableFilterMapper() {}

  public static VariableFilter toUserTaskVariableFilter(
      @Nullable final UserTaskVariableFilter filter) {
    if (filter == null) {
      return FilterBuilders.variable().build();
    }
    final var builder = FilterBuilders.variable();
    ofNullable(filter.getName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::nameOperations);
    return builder.build();
  }
}
