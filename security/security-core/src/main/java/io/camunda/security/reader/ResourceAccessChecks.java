/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.condition.AuthorizationCondition;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record ResourceAccessChecks(AuthorizationCheck authorizationCheck, TenantCheck tenantCheck) {

  public static ResourceAccessChecks disabled() {
    return new ResourceAccessChecks(AuthorizationCheck.disabled(), TenantCheck.disabled());
  }

  public static ResourceAccessChecks of(
      final AuthorizationCheck authorizationCheck, final TenantCheck tenantCheck) {
    return new ResourceAccessChecks(authorizationCheck, tenantCheck);
  }

  /**
   * Returns a mapping from authorization resource type name to the list of all resource IDs
   * authorized by any branch of the current {@link AuthorizationCondition}.
   *
   * <p>If authorization checks are disabled, the condition is {@code null}, or no authorization
   * branches are present, this method returns an empty map.
   *
   * <p>For composite authorization conditions (e.g., {@code AnyOfAuthorizationCondition}), multiple
   * branches may reference the same resource type. In such cases, all resource IDs for that type
   * are merged into a single {@link List}, ensuring uniqueness and preserving order. Authorizations
   * with {@code null} or empty resource ID lists are ignored.
   *
   * <p>Authorizations with {@code null} or empty resource IDs list are ignored entirely. As a
   * result, this method never produces entries that map a resource type to an empty list. Only
   * resource types with at least one valid ID appear in the returned map.
   *
   * @return a map where each key is a resource type name and each value is the list of all resource
   *     IDs authorized for that type across all applicable authorization branches
   */
  public Map<String, List<String>> getAuthorizedResourceIdsByType() {
    if (!authorizationCheck.enabled() || !authorizationCheck.hasAnyResourceAccess()) {
      return Collections.emptyMap();
    }

    final var auths = authorizationCheck.authorizationCondition().authorizations();
    return auths.stream()
        .filter(Objects::nonNull)
        .filter(auth -> auth.resourceType() != null)
        .filter(Authorization::hasAnyResourceIds)
        .collect(
            Collectors.groupingBy(
                auth -> auth.resourceType().name(),
                Collectors.collectingAndThen(
                    // collect IDs into a LinkedHashSet to ensure uniqueness + stable order
                    Collectors.flatMapping(
                        auth -> auth.resourceIds().stream(),
                        Collectors.toCollection(LinkedHashSet::new)),
                    // then convert that Set to an unmodifiable List for the API
                    List::copyOf)));
  }

  public List<String> getAuthorizedTenantIds() {
    if (!tenantCheck.hasAnyTenantAccess()) {
      return Collections.emptyList();
    }

    return tenantCheck.tenantIds();
  }
}
