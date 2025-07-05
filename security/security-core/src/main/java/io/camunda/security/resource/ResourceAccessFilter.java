/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.resource;

import java.util.function.Function;

public record ResourceAccessFilter(
    AuthorizationBasedResourceAccessFilter authorizationFilter,
    TenantBasedResourceAccessFilter tenantFilter) {

  public static ResourceAccessFilter of(final Function<Builder, Builder> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder {
    private AuthorizationBasedResourceAccessFilter authorizationFilter;
    private TenantBasedResourceAccessFilter tenantFilter;

    public Builder authorizationFilter(final AuthorizationBasedResourceAccessFilter value) {
      authorizationFilter = value;
      return this;
    }

    public Builder tenantFilter(final TenantBasedResourceAccessFilter value) {
      tenantFilter = value;
      return this;
    }

    public ResourceAccessFilter build() {
      return new ResourceAccessFilter(authorizationFilter, tenantFilter);
    }
  }
}
