/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration.app.adapter;

import io.camunda.gatekeeper.model.identity.CamundaUserInfo;
import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.gatekeeper.spi.CamundaUserProvider;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Stub user provider that assembles {@link CamundaUserInfo} from the authentication context.
 * Profile data (display name, email) is resolved from an in-memory map.
 */
@Component
public final class StubCamundaUserProvider implements CamundaUserProvider {

  private static final Map<String, String[]> PROFILES =
      Map.of(
          "demo", new String[] {"Demo User", "demo@example.com"},
          "operator", new String[] {"Operator", "operator@example.com"});

  private final CamundaAuthenticationProvider authProvider;

  public StubCamundaUserProvider(final CamundaAuthenticationProvider authProvider) {
    this.authProvider = authProvider;
  }

  @Override
  public CamundaUserInfo getCurrentUser() {
    final var auth = authProvider.getCamundaAuthentication();
    if (auth == null || auth.isAnonymous()) {
      return null;
    }

    final var username = auth.authenticatedUsername();
    final var profile = PROFILES.get(username);

    final var tenants =
        auth.authenticatedTenantIds() != null
            ? auth.authenticatedTenantIds().stream()
                .map(id -> new CamundaUserInfo.Tenant(id, null, null))
                .toList()
            : List.<CamundaUserInfo.Tenant>of();
    return new CamundaUserInfo(
        profile != null ? profile[0] : username,
        username,
        profile != null ? profile[1] : null,
        List.of(),
        tenants,
        auth.authenticatedGroupIds(),
        auth.authenticatedRoleIds(),
        null,
        Map.of(),
        true);
  }

  @Override
  public String getUserToken() {
    return null;
  }
}
