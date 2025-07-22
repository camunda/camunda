/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.oauth;

import io.camunda.identity.sdk.Identity;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.tenant.TasklistTenant;
import io.camunda.tasklist.webapp.tenant.TenantAwareAuthentication;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class IdentityTenantAwareJwtAuthenticationToken extends JwtAuthenticationToken
    implements TenantAwareAuthentication {

  private static final long serialVersionUID = 1L;

  private List<TasklistTenant> tenants = Collections.emptyList();

  public IdentityTenantAwareJwtAuthenticationToken(
      final Jwt jwt, final Collection<? extends GrantedAuthority> authorities, final String name) {
    super(jwt, authorities, name);
  }

  @Override
  public List<TasklistTenant> getTenants() {
    if (CollectionUtils.isEmpty(tenants) && isMultiTenancyEnabled()) {
      tenants = retrieveTenants();
    }
    return tenants;
  }

  private List<TasklistTenant> retrieveTenants() {
    try {
      final var token = getToken().getTokenValue();
      final var identityTenants = getIdentity().tenants().forToken(token);
      if (CollectionUtils.isEmpty(identityTenants)) {
        return Collections.emptyList();
      } else {
        return identityTenants.stream()
            .map((t) -> new TasklistTenant(t.getTenantId(), t.getName()))
            .sorted(TENANT_NAMES_COMPARATOR)
            .toList();
      }
    } catch (final Exception e) {
      throw new InsufficientAuthenticationException(e.getMessage(), e);
    }
  }

  private Identity getIdentity() {
    return SpringContextHolder.getBean(Identity.class);
  }

  private boolean isMultiTenancyEnabled() {
    return SpringContextHolder.getBean(SecurityConfiguration.class).getMultiTenancy().isEnabled();
  }
}
