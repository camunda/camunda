/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Objects;
import java.util.function.Function;

public record TypedPermissionCheck<T>(
    AuthorizationResourceType resourceType,
    PermissionType permissionType,
    String resourceId,
    Function<T, String> resourceIdSupplier) {

  public static <T> TypedPermissionCheck<T> of(final Function<Builder<T>, Builder<T>> fn) {
    return fn.apply(new Builder<>()).build();
  }

  public static class Builder<T> {

    private AuthorizationResourceType resourceType;
    private PermissionType permissionType;
    private String resourceId;
    private Function<T, String> resourceIdSupplier;

    public Builder<T> resourceType(final AuthorizationResourceType value) {
      resourceType = value;
      return this;
    }

    public Builder<T> permissionType(final PermissionType value) {
      permissionType = value;
      return this;
    }

    public Builder<T> resourceId(final String value) {
      resourceId = value;
      return this;
    }

    public Builder<T> resourceIdSupplier(final Function<T, String> value) {
      resourceIdSupplier = value;
      return this;
    }

    public TypedPermissionCheck<T> build() {
      return new TypedPermissionCheck<T>(
          Objects.requireNonNull(resourceType),
          Objects.requireNonNull(permissionType),
          resourceId,
          resourceIdSupplier);
    }
  }
}
