/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.resource;

import java.util.List;

public record TenantResult(
    boolean granted, boolean requiresCheck, List<String> authenticatedTenants) {

  public boolean forbidden() {
    return !granted && !requiresCheck;
  }

  public static TenantResult successful() {
    return new TenantResult(true, false, List.of());
  }

  public static TenantResult unsuccessful() {
    return new TenantResult(false, false, List.of());
  }

  public static TenantResult tenantCheckRequired(final List<String> authenticatedTenants) {
    return new TenantResult(false, true, authenticatedTenants);
  }
}
