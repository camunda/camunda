/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import io.camunda.security.auth.CamundaAuthentication;

public interface TenantAccessProvider {

  /**
   * Resolves the given {@link CamundaAuthentication authentication} into a {@link TenantAccess}.
   * The resulting {@link TenantAccess#tenantIds()} contains the tenant ids the principal is granted
   * to access if access is not denied.
   */
  TenantAccess resolveTenantAccess(final CamundaAuthentication authentication);

  /**
   * Returns a {@link TenantAccess} allowing or denying access to the given resource based on the
   * tenant.
   */
  <T> TenantAccess hasTenantAccess(CamundaAuthentication authentication, T resource);

  /** Returns a {@link TenantAccess} allowing or denying access to the given tenant id. */
  TenantAccess hasTenantAccessByTenantId(CamundaAuthentication authentication, String tenantId);
}
