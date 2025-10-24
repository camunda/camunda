/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import io.camunda.security.auth.Authorization;
import java.util.List;
import java.util.Optional;

public record ResourceAccessChecks(AuthorizationCheck authorizationCheck, TenantCheck tenantCheck) {

  public static ResourceAccessChecks disabled() {
    return new ResourceAccessChecks(AuthorizationCheck.disabled(), TenantCheck.disabled());
  }

  public static ResourceAccessChecks of(
      final AuthorizationCheck authorizationCheck, final TenantCheck tenantCheck) {
    return new ResourceAccessChecks(authorizationCheck, tenantCheck);
  }

  public List<String> getAuthorizedResourceIds() {
    if (authorizationCheck == null || !authorizationCheck.enabled()) {
      return List.of();
    }

    return Optional.of(authorizationCheck)
        .map(AuthorizationCheck::authorization)
        .map(Authorization::resourceIds)
        .orElse(List.of());
  }

  public List<String> getAuthorizedTenantIds() {
    if (tenantCheck == null || !tenantCheck.enabled()) {
      return List.of();
    }

    return Optional.of(tenantCheck).map(TenantCheck::tenantIds).orElse(List.of());
  }
}
