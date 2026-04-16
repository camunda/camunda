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

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UserFilter;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class UserFilterMapper {

  private UserFilterMapper() {}

  public static UserFilter toUserFilter(final io.camunda.gateway.protocol.model.UserFilter filter) {
    final var builder = FilterBuilders.user();
    if (filter != null) {
      ofNullable(filter.getUsername())
          .map(mapToOperations(String.class))
          .ifPresent(builder::usernameOperations);
      ofNullable(filter.getName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::nameOperations);
      ofNullable(filter.getEmail())
          .map(mapToOperations(String.class))
          .ifPresent(builder::emailOperations);
    }
    return builder.build();
  }
}
