/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationFilterContract;
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
      @Nullable final AuthorizationFilterContract filter) {
    return Optional.ofNullable(filter)
        .map(
            f ->
                FilterBuilders.authorization()
                    .ownerIds(f.ownerId())
                    .ownerType(f.ownerType() == null ? null : f.ownerType().getValue())
                    .resourceIds(f.resourceIds() != null ? f.resourceIds() : List.of())
                    .resourcePropertyNames(
                        f.resourcePropertyNames() != null ? f.resourcePropertyNames() : List.of())
                    .resourceType(f.resourceType() == null ? null : f.resourceType().getValue())
                    .build())
        .orElse(null);
  }
}
