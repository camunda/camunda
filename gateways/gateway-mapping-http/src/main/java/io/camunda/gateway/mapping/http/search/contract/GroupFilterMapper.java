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
import io.camunda.search.filter.GroupFilter;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class GroupFilterMapper {

  private GroupFilterMapper() {}

  public static GroupFilter toGroupFilter(
      final io.camunda.gateway.protocol.model.GroupFilter filter) {
    final var builder = FilterBuilders.group();
    if (filter != null) {
      ofNullable(filter.getGroupId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::groupIdOperations);
      ofNullable(filter.getName()).ifPresent(builder::name);
    }
    return builder.build();
  }
}
