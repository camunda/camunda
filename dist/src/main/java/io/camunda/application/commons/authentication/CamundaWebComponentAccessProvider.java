/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import static io.camunda.service.authorization.Authorizations.COMPONENT_ACCESS_AUTHORIZATION;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.WebComponentAccessProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.ResourceAccessProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "camunda.auth.sdk.enabled", havingValue = "true")
@ConditionalOnAnyHttpGatewayEnabled
public class CamundaWebComponentAccessProvider implements WebComponentAccessProvider {

  private final SecurityConfiguration securityConfig;
  private final ResourceAccessProvider resourceAccessProvider;

  public CamundaWebComponentAccessProvider(
      final SecurityConfiguration securityConfig,
      final ResourceAccessProvider resourceAccessProvider) {
    this.securityConfig = securityConfig;
    this.resourceAccessProvider = resourceAccessProvider;
  }

  @Override
  public boolean isAuthorizationEnabled() {
    return securityConfig.getAuthorizations().isEnabled();
  }

  @Override
  public boolean hasAccessToComponent(
      final CamundaAuthentication authentication, final String component) {
    // We want to temporarily support both "admin" and "identity" as components for the
    // authorization check, for backwards compatibility.
    // TODO(#46027): Drop support for "identity" component for 8.10
    if ("admin".equals(component) || "identity".equals(component)) {
      return resourceAccessProvider
              .hasResourceAccessByResourceId(
                  authentication, COMPONENT_ACCESS_AUTHORIZATION, "identity")
              .allowed()
          || resourceAccessProvider
              .hasResourceAccessByResourceId(
                  authentication, COMPONENT_ACCESS_AUTHORIZATION, "admin")
              .allowed();
    }
    return resourceAccessProvider
        .hasResourceAccessByResourceId(authentication, COMPONENT_ACCESS_AUTHORIZATION, component)
        .allowed();
  }

  @Override
  public List<String> getAuthorizedComponents(final CamundaAuthentication authentication) {
    final var componentAccess =
        resourceAccessProvider.resolveResourceAccess(
            authentication, COMPONENT_ACCESS_AUTHORIZATION);
    return componentAccess.allowed() ? componentAccess.authorization().resourceIds() : List.of();
  }
}
