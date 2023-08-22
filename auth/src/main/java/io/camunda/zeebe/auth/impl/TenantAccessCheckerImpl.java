/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.auth.impl;

import io.camunda.zeebe.auth.api.TenantAccessChecker;
import java.util.List;

public class TenantAccessCheckerImpl implements TenantAccessChecker {

  private final List<String> authorizedTenants;

  public TenantAccessCheckerImpl(final List<String> authorizedTenants) {
    this.authorizedTenants = authorizedTenants;
  }

  @Override
  public Boolean hasAccess(final String tenantId) {
    return authorizedTenants.stream().anyMatch(tenant -> tenant.equals(tenantId));
  }

  @Override
  public Boolean hasFullAccess(final List<String> tenantIds) {
    return authorizedTenants.containsAll(tenantIds);
  }
}
