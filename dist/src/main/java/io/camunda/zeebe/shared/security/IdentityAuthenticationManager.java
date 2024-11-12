/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.security;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public final class IdentityAuthenticationManager implements AuthenticationManager {

  private final Identity identity;
  private final MultiTenancyCfg multiTenancy;

  @Autowired
  public IdentityAuthenticationManager(
      final Identity identity, final MultiTenancyCfg multiTenancy) {
    this.identity = identity;
    this.multiTenancy = multiTenancy;
  }

  @Override
  public Authentication authenticate(final Authentication authentication) {
    if (!(authentication instanceof final PreAuthToken preAuthToken)) {
      return authentication;
    }

    final List<String> tenants;
    final var tokenValue = preAuthToken.token();
    final AccessToken token;

    try {
      token = identity.authentication().verifyToken(tokenValue);
    } catch (final Exception e) {
      throw new BadCredentialsException(e.getMessage(), e);
    }

    tenants = getTenants(tokenValue);

    return new IdentityAuthentication(token, tenants);
  }

  private List<String> getTenants(final String token) {
    if (!multiTenancy.isEnabled()) {
      return Collections.singletonList(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    try {
      return identity.tenants().forToken(token).stream().map(Tenant::getTenantId).toList();
    } catch (final RuntimeException e) {
      throw new InternalAuthenticationServiceException(
          "Expected Identity to provide authorized tenants, see cause for details", e);
    }
  }
}
