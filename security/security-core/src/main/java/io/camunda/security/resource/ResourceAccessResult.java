/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.resource;

import java.util.function.Function;

public record ResourceAccessResult(
    AuthorizationResult authorizationResult, TenantResult tenantFilter) {

  public static ResourceAccessResult of(final Function<Builder, Builder> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder {
    private AuthorizationResult authorizationResult;
    private TenantResult tenantFilter;

    public Builder authorizationResult(final AuthorizationResult value) {
      authorizationResult = value;
      return this;
    }

    public Builder tenantResult(final TenantResult value) {
      tenantFilter = value;
      return this;
    }

    public ResourceAccessResult build() {
      return new ResourceAccessResult(authorizationResult, tenantFilter);
    }
  }
}
