/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.adapter;

import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;

import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.ResourceAccessProvider;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(ResourceAccessProvider.class)
public final class WebComponentAccessAdapter {

  private static final List<String> WEB_COMPONENTS =
      List.of("identity", "admin", "operate", "tasklist");

  private final ResourceAccessProvider resourceAccessProvider;
  private final SecurityConfiguration securityConfiguration;

  public WebComponentAccessAdapter(
      final ResourceAccessProvider resourceAccessProvider,
      final SecurityConfiguration securityConfiguration) {
    this.resourceAccessProvider = resourceAccessProvider;
    this.securityConfiguration = securityConfiguration;
  }

  public boolean isAuthorizationEnabled() {
    return securityConfiguration.getAuthorizations().isEnabled();
  }

  public boolean hasAccessToComponent(
      final CamundaAuthentication authentication, final String component) {
    final var componentAccess =
        resourceAccessProvider.resolveResourceAccess(
            authentication, COMPONENT_ACCESS_AUTHORIZATION);
    if (!componentAccess.allowed()) {
      return false;
    }
    final var authorization = componentAccess.authorization();
    return authorization.isWildcard() || authorization.resourceIds().contains(component);
  }

  public List<String> getAuthorizedComponents(final CamundaAuthentication authentication) {
    return WEB_COMPONENTS.stream()
        .filter(component -> hasAccessToComponent(authentication, component))
        .collect(Collectors.toList());
  }
}
