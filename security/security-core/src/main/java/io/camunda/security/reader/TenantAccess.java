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

  public boolean denied() {
    return !allowed;
  }

  public static TenantAccess allowed(final List<String> tenantIds) {
    return new TenantAccess(true, false, tenantIds);
  }

  public static TenantAccess denied(final List<String> tenantIds) {
    return new TenantAccess(false, false, tenantIds);
  }

  public static TenantAccess wildcard(final List<String> tenantIds) {
    return new TenantAccess(true, true, tenantIds);
  }
}
