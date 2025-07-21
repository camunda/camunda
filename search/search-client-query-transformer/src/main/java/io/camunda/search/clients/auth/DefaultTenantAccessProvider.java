/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.reader.TenantAccess;
import io.camunda.security.reader.TenantAccessProvider;

public class DefaultTenantAccessProvider implements TenantAccessProvider {

  @Override
  public TenantAccess resolveTenantAccess(final CamundaAuthentication authentication) {
    final var authenticatedTenantIds = authentication.authenticatedTenantIds();
    if (authenticatedTenantIds == null || authenticatedTenantIds.isEmpty()) {
      return TenantAccess.denied(null);
    }
    return TenantAccess.allowed(authenticatedTenantIds);
  }
}
