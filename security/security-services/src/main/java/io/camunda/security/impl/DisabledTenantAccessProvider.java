/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import io.camunda.search.clients.security.TenantAccess;
import io.camunda.search.clients.security.TenantAccessProvider;
import io.camunda.security.auth.CamundaAuthentication;

public class DisabledTenantAccessProvider implements TenantAccessProvider {

  @Override
  public TenantAccess resolveTenantAccess(final CamundaAuthentication authentication) {
    return TenantAccess.all();
  }

  @Override
  public <T> TenantAccess hasTenantAccess(
      final CamundaAuthentication authentication, final T resource) {
    return TenantAccess.all();
  }

  @Override
  public TenantAccess hasTenantAccess(
      final CamundaAuthentication authentication, final String tenantId) {
    return TenantAccess.all();
  }
}
