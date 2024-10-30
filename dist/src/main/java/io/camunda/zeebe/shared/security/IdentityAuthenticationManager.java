/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.security;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.zeebe.gateway.impl.configuration.ExperimentalCfg;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.gateway.impl.identity.IdentityTenantService;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class IdentityAuthenticationManager implements ReactiveAuthenticationManager {

  private final Identity identity;
  private final IdentityTenantService tenantService;
  private final MultiTenancyCfg multiTenancy;

  @Autowired
  public IdentityAuthenticationManager(
      final Identity identity,
      final MultiTenancyCfg multiTenancy,
      final ExperimentalCfg experimentalCfg) {
    this.identity = identity;
    this.multiTenancy = multiTenancy;
    tenantService = new IdentityTenantService(identity, experimentalCfg.getIdentityRequest());
  }

  @Override
  public Mono<Authentication> authenticate(final Authentication authentication) {
    if (!(authentication instanceof final PreAuthToken preAuthToken)) {
      return Mono.just(authentication);
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

    return Mono.just(new IdentityAuthentication(token, tenants));
  }

  private List<String> getTenants(final String token) {
    if (!multiTenancy.isEnabled()) {
      return Collections.singletonList(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    try {
      return tenantService.getTenantsForToken(token).stream().map(Tenant::getTenantId).toList();
    } catch (final RuntimeException | ExecutionException e) {
      throw new InternalAuthenticationServiceException(
          "Expected Identity to provide authorized tenants, see cause for details", e);
    }
  }
}
