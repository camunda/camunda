/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.user;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.CamundaUserInfo;
import io.camunda.auth.domain.model.TenantInfo;
import io.camunda.auth.domain.model.UserProfile;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.domain.spi.CamundaUserProvider;
import io.camunda.auth.domain.spi.TenantInfoProvider;
import io.camunda.auth.domain.spi.UserProfileProvider;
import io.camunda.auth.domain.spi.WebComponentAccessProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** CamundaUserProvider implementation for Basic auth. Uses SPIs to resolve user profile data. */
public class BasicCamundaUserProvider implements CamundaUserProvider {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final WebComponentAccessProvider componentAccessProvider;
  private final UserProfileProvider userProfileProvider;
  private final TenantInfoProvider tenantInfoProvider;

  public BasicCamundaUserProvider(
      final CamundaAuthenticationProvider authenticationProvider,
      final WebComponentAccessProvider componentAccessProvider,
      final UserProfileProvider userProfileProvider,
      final TenantInfoProvider tenantInfoProvider) {
    this.authenticationProvider = authenticationProvider;
    this.componentAccessProvider = componentAccessProvider;
    this.userProfileProvider = userProfileProvider;
    this.tenantInfoProvider = tenantInfoProvider;
  }

  @Override
  public CamundaUserInfo getCurrentUser() {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return Optional.ofNullable(authentication)
        .filter(a -> !a.isAnonymous())
        .map(this::buildUserInfo)
        .orElse(null);
  }

  @Override
  public String getUserToken() {
    return null;
  }

  private CamundaUserInfo buildUserInfo(final CamundaAuthentication authentication) {
    final var username = authentication.authenticatedUsername();
    final var profile =
        Optional.ofNullable(userProfileProvider.getUserProfile(username))
            .orElse(new UserProfile(username, null));
    final var tenants = resolveTenants(authentication);
    final var authorizedComponents =
        componentAccessProvider.getAuthorizedComponents(authentication);
    return new CamundaUserInfo(
        profile.displayName(),
        username,
        profile.email(),
        authorizedComponents,
        tenants,
        authentication.authenticatedGroupIds(),
        authentication.authenticatedRoleIds(),
        true,
        Map.of());
  }

  private List<TenantInfo> resolveTenants(final CamundaAuthentication authentication) {
    final var tenantIds = authentication.authenticatedTenantIds();
    if (tenantIds == null || tenantIds.isEmpty()) {
      return List.of();
    }
    return tenantInfoProvider.getTenants(tenantIds);
  }
}
