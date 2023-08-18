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

  private final List<TenantImpl> authorizedTenants;

  public TenantAccessCheckerImpl(final List<TenantImpl> authorizedTenants) {
    this.authorizedTenants = authorizedTenants;
  }

  @Override
  public Boolean hasAccess(final String tenantId) {
    return authorizedTenants.stream().anyMatch(tenant -> tenant.getId().equals(tenantId));
  }

  @Override
  public Boolean hasFullAccess(final List<String> tenantIds) {
    return authorizedTenants.stream().map(TenantImpl::getId).toList().containsAll(tenantIds);
  }

  public static TenantAccessChecker from(final List<String> authorizedTenantIds) {
    final List<TenantImpl> authorizedTenants =
        authorizedTenantIds.stream().map(TenantImpl::new).toList();
    return new TenantAccessCheckerImpl(authorizedTenants);
  }
}
