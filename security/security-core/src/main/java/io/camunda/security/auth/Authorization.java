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
import java.util.function.Function;

public record Authorization(AuthorizationResourceType resourceType, PermissionType permissionType) {
  public static Authorization of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static class Builder {
    private AuthorizationResourceType resourceType;
    private PermissionType permissionType;

    public Builder resourceType(final AuthorizationResourceType resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder permissionType(final PermissionType permissionType) {
      this.permissionType = permissionType;
      return this;
    }

    public Authorization build() {
      return new Authorization(resourceType, permissionType);
    }
  }
}
