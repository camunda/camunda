/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValuesAsList;

import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record AuthorizationFilter(
    Long authorizationKey,
    List<String> ownerIds,
    String ownerType,
    Short resourceMatcher,
    List<String> resourceIds,
    String resourceType,
    List<PermissionType> permissionTypes,
    Map<EntityType, Set<String>> ownerTypeToOwnerIds)
    implements FilterBase {
  public static final class Builder implements ObjectBuilder<AuthorizationFilter> {
    private Long authorizationKey;
    private List<String> ownerIds;
    private String ownerType;
    private List<String> resourceIds;
    private Short resourceMatcher;
    private String resourceType;
    private List<PermissionType> permissionTypes;
    private Map<EntityType, Set<String>> ownerTypeToOwnerIds;

    public Builder authorizationKey(final Long value) {
      authorizationKey = value;
      return this;
    }

    public Builder ownerIds(final List<String> value) {
      ownerIds = addValuesToList(ownerIds, value);
      return this;
    }

    public Builder ownerIds(final String... values) {
      return ownerIds(collectValuesAsList(values));
    }

    public Builder ownerType(final String value) {
      ownerType = value;
      return this;
    }

    public Builder resourceMatcher(final AuthorizationResourceMatcher value) {
      resourceMatcher = value.value();
      return this;
    }

    public Builder resourceIds(final List<String> value) {
      resourceIds = value;
      return this;
    }

    public Builder resourceIds(final String... values) {
      return resourceIds(collectValuesAsList(values));
    }

    public Builder resourceType(final String value) {
      resourceType = value;
      return this;
    }

    public Builder permissionTypes(final List<PermissionType> value) {
      permissionTypes = addValuesToList(permissionTypes, value);
      return this;
    }

    public Builder permissionTypes(final PermissionType... values) {
      return permissionTypes(collectValuesAsList(values));
    }

    public Builder ownerTypeToOwnerIds(final Map<EntityType, Set<String>> value) {
      ownerTypeToOwnerIds = value;
      return this;
    }

    private Map<EntityType, Set<String>> getValidOwnerTypeToOwnerIdsOrThrow() {
      if (ownerTypeToOwnerIds == null || ownerTypeToOwnerIds.isEmpty()) {
        return null;
      }

      final var ownerTypeWithoutOwnerIds =
          ownerTypeToOwnerIds.entrySet().stream()
              .filter(e -> e.getValue() == null || e.getValue().isEmpty())
              .findFirst();

      if (ownerTypeWithoutOwnerIds.isPresent()) {
        final var message =
            "Owner type to owner ids must not contain entries without a value: %s"
                .formatted(ownerTypeWithoutOwnerIds.get());
        throw new IllegalArgumentException(message);
      }

      return ownerTypeToOwnerIds;
    }

    @Override
    public AuthorizationFilter build() {
      return new AuthorizationFilter(
          authorizationKey,
          ownerIds,
          ownerType,
          resourceMatcher,
          resourceIds,
          resourceType,
          permissionTypes,
          getValidOwnerTypeToOwnerIdsOrThrow());
    }
  }
}
