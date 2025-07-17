/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import java.util.List;

public record TenantAccess(boolean allowed, boolean wildcard, List<String> tenantIds) {

  /** Returns true if the access to all provided tenantIds is denied. */
  public boolean denied() {
    return !allowed;
  }

  /**
   * Creates a {@link TenantAccess} allowing access the tenants passed with {@link List<String>
   * tenantIds}.
   */
  public static TenantAccess allowed(final List<String> tenantIds) {
    return new TenantAccess(true, false, tenantIds);
  }

  /**
   * Creates a {@link TenantAccess} denying access to any tenant passed with {@link List<String>
   * tenantIds}.
   */
  public static TenantAccess denied(final List<String> tenantIds) {
    return new TenantAccess(false, false, tenantIds);
  }

  /** Creates a {@link TenantAccess} allowing wildcard access to any tenant. */
  public static TenantAccess wildcard(final List<String> tenantIds) {
    return new TenantAccess(true, true, tenantIds);
  }
}
