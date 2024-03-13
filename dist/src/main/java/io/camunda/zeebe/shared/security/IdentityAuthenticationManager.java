/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.security;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public final class IdentityAuthenticationManager implements ReactiveAuthenticationManager {

  private final Identity identity;
  private final MultiTenancyCfg multiTenancyConfig;

  @Autowired
  public IdentityAuthenticationManager(
      final Identity identity, final MultiTenancyCfg multiTenancyConfig) {
    this.identity = identity;
    this.multiTenancyConfig = multiTenancyConfig;
  }

  @Override
  public Mono<Authentication> authenticate(final Authentication authentication) {
    if (!(authentication instanceof PreAuthToken preAuthToken)) {
      return Mono.just(authentication);
    }

    final List<String> tenants;
    final var tokenValue = preAuthToken.token();

    try {
      final var token = identity.authentication().verifyToken(tokenValue);
      if (multiTenancyConfig.isEnabled()) {
        tenants =
            identity.tenants().forToken(tokenValue).stream().map(Tenant::getTenantId).toList();
      } else {
        tenants = Collections.singletonList(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
      }

      return Mono.just(new IdentityAuthentication(token, tenants));
    } catch (final Exception e) {
      throw new BadCredentialsException(e.getMessage(), e);
    }
  }
}
