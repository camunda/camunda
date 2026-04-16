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
                    .ownerIds(f.getOwnerId())
                    .ownerType(f.getOwnerType() == null ? null : f.getOwnerType().getValue())
                    .resourceIds(f.getResourceIds() != null ? f.getResourceIds() : List.of())
                    .resourcePropertyNames(
                        f.getResourcePropertyNames() != null
                            ? f.getResourcePropertyNames()
                            : List.of())
                    .resourceType(
                        f.getResourceType() == null ? null : f.getResourceType().getValue())
                    .build())
        .orElse(null);
  }
}
