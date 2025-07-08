/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.security;

import java.util.List;

public record TenantAccess(boolean granted, boolean wildcard, List<String> tenantIds) {

  public boolean hasAccessToAllTenants() {
    return granted && wildcard;
  }

  public boolean forbidden() {
    return !granted;
  }

  public static TenantAccess all() {
    return new TenantAccess(true, true, List.of());
  }

  public static TenantAccess allowed(final List<String> tenantIds) {
    return new TenantAccess(true, false, tenantIds);
  }

  public static TenantAccess denied(final List<String> tenantIds) {
    return new TenantAccess(false, false, tenantIds);
  }
}
