/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import io.camunda.search.entities.TenantOwnedEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.reader.TenantAccess;
import io.camunda.security.reader.TenantAccessProvider;
import java.util.List;

public class DefaultTenantAccessProvider implements TenantAccessProvider {

  @Override
  public TenantAccess resolveTenantAccess(final CamundaAuthentication authentication) {
    final var authenticatedTenantIds = authentication.authenticatedTenantIds();
    if (authenticatedTenantIds == null || authenticatedTenantIds.isEmpty()) {
      return TenantAccess.denied(null);
    }
    return TenantAccess.allowed(authenticatedTenantIds);
  }

  @Override
  public <T> TenantAccess hasTenantAccess(
      final CamundaAuthentication authentication, final T resource) {
    if (resource instanceof final TenantOwnedEntity tenantOwnedEntity
        && tenantOwnedEntity.hasTenantScope()) {
      return hasTenantAccessByTenantId(authentication, tenantOwnedEntity.tenantId());
    }
    // if not tenant-owned, no tenant check needed => access granted
    return TenantAccess.allowed(List.of());
  }

  @Override
  public TenantAccess hasTenantAccessByTenantId(
      final CamundaAuthentication authentication, final String tenantId) {
    final var authenticatedTenantIds = authentication.authenticatedTenantIds();
    final var tenantIdAsList = List.of(tenantId);

    if (authenticatedTenantIds == null || authenticatedTenantIds.isEmpty()) {
      return TenantAccess.denied(tenantIdAsList);
    }

    return authenticatedTenantIds.contains(tenantId)
        ? TenantAccess.allowed(tenantIdAsList)
        : TenantAccess.denied(tenantIdAsList);
  }
}
