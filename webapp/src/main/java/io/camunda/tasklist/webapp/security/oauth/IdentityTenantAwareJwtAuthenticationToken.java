/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.oauth;

import io.camunda.identity.sdk.Identity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.security.tenant.TasklistTenant;
import io.camunda.tasklist.webapp.security.tenant.TenantAwareAuthentication;
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
    } catch (Exception e) {
      throw new InsufficientAuthenticationException(e.getMessage(), e);
    }
  }

  private Identity getIdentity() {
    return SpringContextHolder.getBean(Identity.class);
  }

  private boolean isMultiTenancyEnabled() {
    return SpringContextHolder.getBean(TasklistProperties.class).getMultiTenancy().isEnabled();
  }
}
