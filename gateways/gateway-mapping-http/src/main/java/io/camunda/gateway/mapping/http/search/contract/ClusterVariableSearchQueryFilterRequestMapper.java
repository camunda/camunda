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

import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.FilterBuilders;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ClusterVariableSearchQueryFilterRequestMapper {

  private ClusterVariableSearchQueryFilterRequestMapper() {}

  public static ClusterVariableFilter toClusterVariableFilter(
      final io.camunda.gateway.protocol.model.ClusterVariableSearchQueryFilterRequest filter) {
    if (filter == null) {
      return FilterBuilders.clusterVariable().build();
    }
    final var builder = FilterBuilders.clusterVariable();
    ofNullable(filter.getName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::nameOperations);
    ofNullable(filter.getValue())
        .map(mapToOperations(String.class))
        .ifPresent(builder::valueOperations);
    ofNullable(filter.getScope())
        .map(mapToOperations(String.class))
        .ifPresent(builder::scopeOperations);
    ofNullable(filter.getTenantId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::tenantIdOperations);
    ofNullable(filter.getIsTruncated()).ifPresent(builder::isTruncated);
    return builder.build();
  }
}
