/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filter;

import io.camunda.gatekeeper.spi.CamundaAuthenticationProvider;
import io.camunda.gatekeeper.spring.filter.WebappFilterChainCustomizer;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.ResourceAccessProvider;
import java.util.Optional;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.stereotype.Component;

/**
 * Registers the {@link WebComponentAuthorizationCheckFilter} on gatekeeper's webapp security filter
 * chains via the {@link WebappFilterChainCustomizer} extension point. Only adds the filter when a
 * {@link ResourceAccessProvider} is available (i.e., when secondary storage is enabled).
 */
@Component
public final class WebComponentAuthorizationFilterChainCustomizer
    implements WebappFilterChainCustomizer {

  private final SecurityConfiguration securityConfiguration;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final Optional<ResourceAccessProvider> resourceAccessProvider;

  public WebComponentAuthorizationFilterChainCustomizer(
      final SecurityConfiguration securityConfiguration,
      final CamundaAuthenticationProvider authenticationProvider,
      final Optional<ResourceAccessProvider> resourceAccessProvider) {
    this.securityConfiguration = securityConfiguration;
    this.authenticationProvider = authenticationProvider;
    this.resourceAccessProvider = resourceAccessProvider;
  }

  @Override
  public void customize(final HttpSecurity http) throws Exception {
    if (resourceAccessProvider.isPresent()) {
      http.addFilterAfter(
          new WebComponentAuthorizationCheckFilter(
              securityConfiguration, authenticationProvider, resourceAccessProvider.get()),
          AuthorizationFilter.class);
    }
  }
}
