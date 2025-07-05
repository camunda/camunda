/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.control;

import io.camunda.security.auth.Authorization;
import java.util.List;
import java.util.function.Function;

public record ResourceAccessControl(ResourceAccess resourceAccess, TenantAccess tenantAccess) {

  public static ResourceAccessControl authorized() {
    return new ResourceAccessControl(ResourceAccess.successful(), TenantAccess.successful());
  }

  public static ResourceAccessControl of(final Function<Builder, Builder> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder {

    private ResourceAccess resourceAccess;
    private TenantAccess tenantCheck;

    public Builder resourceAccess(final ResourceAccess value) {
      resourceAccess = value;
      return this;
    }

    public Builder tenantAccess(final TenantAccess value) {
      tenantCheck = value;
      return this;
    }

    public ResourceAccessControl build() {
      return new ResourceAccessControl(resourceAccess, tenantCheck);
    }
  }

  public record ResourceAccess(boolean required, boolean granted, Authorization authorization) {

    public boolean revoked() {
      return !required && !granted;
    }

    public static ResourceAccess required(final Authorization authorization) {
      return new ResourceAccess(true, false, authorization);
    }

    public static ResourceAccess successful() {
      return new ResourceAccess(false, true, null);
    }

    public static ResourceAccess unsuccessful() {
      return new ResourceAccess(false, false, null);
    }
  }

  public record TenantAccess(boolean required, boolean granted, List<String> tenantIds) {

    public boolean revoked() {
      return !required && !granted;
    }

    public static TenantAccess required(final List<String> tenantIds) {
      return new TenantAccess(true, false, tenantIds);
    }

    public static TenantAccess successful() {
      return new TenantAccess(false, true, null);
    }

    public static TenantAccess unsuccessful() {
      return new TenantAccess(false, false, null);
    }
  }
}
