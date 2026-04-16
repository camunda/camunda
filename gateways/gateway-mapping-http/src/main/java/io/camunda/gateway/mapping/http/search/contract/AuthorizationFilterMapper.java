/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.filter.FilterBuilders;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class AuthorizationFilterMapper {

  private AuthorizationFilterMapper() {}

  public static @Nullable AuthorizationFilter toAuthorizationFilter(
      final io.camunda.gateway.protocol.model.AuthorizationFilter filter) {
    return Optional.ofNullable(filter)
        .map(
            f ->
                FilterBuilders.authorization()
                    .ownerIds(f.getOwnerId().orElse(null))
                    .ownerType(f.getOwnerType().map(ot -> ot.getValue()).orElse(null))
                    .resourceIds(f.getResourceIds().orElse(List.of()))
                    .resourcePropertyNames(f.getResourcePropertyNames().orElse(List.of()))
                    .resourceType(f.getResourceType().map(rt -> rt.getValue()).orElse(null))
                    .build())
        .orElse(null);
  }
}
