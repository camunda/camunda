/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import java.util.List;

/**
 * Enables or disables a {@link TenantCheck}. If enabled, then a list of tenantIds must be provided.
 */
public record TenantCheck(boolean enabled, List<String> tenantIds) {

  public static TenantCheck enabled(final List<String> tenantIds) {
    return new TenantCheck(true, tenantIds);
  }

  public static TenantCheck disabled() {
    return new TenantCheck(false, null);
  }

  public boolean hasAnyTenantAccess() {
    return !enabled || hasAnyTenantIdAccess();
  }

  private boolean hasAnyTenantIdAccess() {
    return tenantIds != null && !tenantIds.isEmpty();
  }
}
