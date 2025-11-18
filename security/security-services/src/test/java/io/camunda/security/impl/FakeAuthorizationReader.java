/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A fake implementation of the AuthorizationReader interface for testing purposes. It stores
 * AuthorizationEntity objects in memory and allows searching and retrieving them by key.
 */
class FakeAuthorizationReader implements AuthorizationReader, AutoCloseable {

  private final List<AuthorizationEntity> authorizations = new ArrayList<>();

  /** Create a new AuthorizationEntity, so it can be found by search and getByKey. */
  public void create(final AuthorizationEntity authorization) {
    authorizations.add(authorization);
  }

  @Override
  public AuthorizationEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return authorizations.stream()
        .filter(a -> a.authorizationKey().equals(key))
        .findAny()
        .orElse(null);
  }

  /**
   * Simple search algorithm by filtering the in-memory list of authorizations.
   *
   * <ul>
   *   <li>if the filter is null, all authorizations match
   *   <li>if a filter field is not null, the corresponding field must match (or contain, for lists)
   *       the value in the filter
   * </ul>
   */
  @Override
  public SearchQueryResult<AuthorizationEntity> search(
      final AuthorizationQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var filter = query.filter();
    final var matches = authorizations.stream().filter(a -> matches(a, filter)).toList();

    if (matches.isEmpty()) {
      return SearchQueryResult.empty();
    }

    final var first = matches.getFirst();
    final var last = matches.getLast();
    return new SearchQueryResult<>(
        matches.size(), false, matches, first.toString(), last.toString());
  }

  private boolean matches(
      final AuthorizationEntity authorization, final AuthorizationFilter filter) {
    if (filter == null) {
      return true;
    }
    if (filter.authorizationKey() != null
        && !filter.authorizationKey().equals(authorization.authorizationKey())) {
      return false;
    }
    if (filter.ownerType() != null && !filter.ownerType().equals(authorization.ownerType())) {
      return false;
    }
    if (filter.ownerIds() != null && !filter.ownerIds().contains(authorization.ownerId())) {
      return false;
    }
    if (filter.ownerTypeToOwnerIds() != null) {
      final Set<String> filterOwnerIds =
          filter
              .ownerTypeToOwnerIds()
              .get(EntityType.valueOf(EntityType.class, authorization.ownerType()));
      if (filterOwnerIds == null || !filterOwnerIds.contains(authorization.ownerId())) {
        return false;
      }
    }
    if (filter.resourceMatcher() != null
        && !filter.resourceMatcher().equals(authorization.resourceMatcher())) {
      return false;
    }
    if (filter.resourceIds() != null
        && !filter.resourceIds().contains(authorization.resourceId())) {
      return false;
    }
    if (filter.resourcePropertyNames() != null
        && !filter.resourcePropertyNames().contains(authorization.resourcePropertyName())) {
      return false;
    }
    if (filter.resourceType() != null
        && !filter.resourceType().equals(authorization.resourceType())) {
      return false;
    }
    return filter.permissionTypes() == null
        || filter.permissionTypes().containsAll(authorization.permissionTypes());
  }

  @Override
  public void close() {
    authorizations.clear();
  }
}
