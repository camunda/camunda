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
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;

public record AuthorizationFilter(
    List<String> ownerIds,
    String ownerType,
    List<String> resourceIds,
    String resourceType,
    PermissionType permissionType)
    implements FilterBase {
  public static final class Builder implements ObjectBuilder<AuthorizationFilter> {
    private List<String> ownerIds;
    private String ownerType;
    private List<String> resourceIds;
    private String resourceType;
    private PermissionType permissionType;

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

    public Builder permissionType(final PermissionType value) {
      permissionType = value;
      return this;
    }

    @Override
    public AuthorizationFilter build() {
      return new AuthorizationFilter(
          ownerIds, ownerType, resourceIds, resourceType, permissionType);
    }
  }
}
