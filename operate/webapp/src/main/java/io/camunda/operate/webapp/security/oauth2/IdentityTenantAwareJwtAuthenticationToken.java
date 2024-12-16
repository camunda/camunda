/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.oauth2;

import io.camunda.identity.sdk.Identity;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.webapp.security.tenant.OperateTenant;
import io.camunda.operate.webapp.security.tenant.TenantAwareAuthentication;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class IdentityTenantAwareJwtAuthenticationToken extends JwtAuthenticationToken
    implements TenantAwareAuthentication {

  private static final long serialVersionUID = 1L;

  private List<OperateTenant> tenants;

  public IdentityTenantAwareJwtAuthenticationToken(
      final Jwt jwt, final Collection<? extends GrantedAuthority> authorities, final String name) {
    super(jwt, authorities, name);
  }

  @Override
  public List<OperateTenant> getTenants() {
    if (tenants == null && isMultiTenancyEnabled()) {
      tenants = retrieveTenants();
    }
    return tenants;
  }

  private List<OperateTenant> retrieveTenants() {
    try {
      final var token = getToken().getTokenValue();
      final var identityTenants = getIdentity().tenants().forToken(token);
      if (identityTenants != null) {
        return identityTenants.stream()
            .map((t) -> new OperateTenant(t.getTenantId(), t.getName()))
            .collect(Collectors.toList());
      } else {
        return new ArrayList<>();
      }
    } catch (final Exception e) {
      // need to trigger HTTP error code 40x. Encapsulate the causing exception
      throw new InsufficientAuthenticationException(e.getMessage(), e);
    }
  }

  private Identity getIdentity() {
    return SpringContextHolder.getBean(Identity.class);
  }

  private SecurityConfiguration getSecurityConfiguration() {
    return SpringContextHolder.getBean(SecurityConfiguration.class);
  }

  private boolean isMultiTenancyEnabled() {
    return getSecurityConfiguration().getMultiTenancy().isEnabled();
  }
}
