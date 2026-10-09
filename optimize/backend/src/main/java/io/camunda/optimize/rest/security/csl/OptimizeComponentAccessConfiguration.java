/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.rest.security.csl.OptimizeWebAppProviderAdapter.OPTIMIZE_WEB_APP;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.core.port.out.AuthorizedComponentsPort;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import io.camunda.security.spring.spi.WebAppAccessDeniedHandlerPort;
import io.camunda.security.spring.spi.WebAppProviderPort;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Enforces access to the Optimize component through CSL's component authorization, driven by the
 * edition's {@link OptimizeComponentAccessPolicy}.
 *
 * <p>{@link OptimizeComponentAccessFilter} asks the policy on every request of an established
 * session, so access ends when the grant is revoked, and a user who may not access Optimize is
 * answered by {@link OptimizeWebAppAccessDeniedAdapter} instead of reaching the application. The
 * filter is added to every chain: Optimize's single page app authenticates its calls with the
 * session cookie, and those calls are served by the API chain. Bearer requests stay unaffected,
 * because {@link OptimizeWebAppProviderAdapter} claims login sessions only.
 *
 * <p>{@link WebAppProviderPort} and {@link WebAppAccessDeniedHandlerPort} are deliberately not
 * exposed as beans. Their presence makes CSL build its own {@code WebAppAuthorizationCheckFilter},
 * which exempts requests by the shape of their URI, and Optimize must not exempt any, see {@link
 * OptimizeComponentAccessFilter}.
 */
@Configuration
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnBean(OptimizeComponentAccessPolicy.class)
public class OptimizeComponentAccessConfiguration {

  @Bean
  public AuthorizationCheckPort authorizationCheckPort(final OptimizeComponentAccessPolicy policy) {
    return new OptimizeComponentAuthorizationAdapter(policy);
  }

  /** Feeds {@code authorizedComponents} of the current user reported by {@code /me}. */
  @Bean
  public AuthorizedComponentsPort authorizedComponentsPort(
      final OptimizeComponentAccessPolicy policy) {
    return authentication ->
        policy.checkAccess(authentication).isRight() ? List.of(OPTIMIZE_WEB_APP) : List.of();
  }

  /**
   * Adds the component check to every chain, so a session-authenticated API call is checked as
   * well, and binds the chain's request so the check can read the session's access token. Both
   * filters are {@code OncePerRequestFilter}s and the same instances on every chain, so each runs
   * once per request.
   */
  @Bean
  public SecurityHeadersCustomizer componentAccessFilters(
      final AuthorizationCheckPort authorizationCheckPort,
      final CamundaAuthenticationProvider authenticationProvider) {
    final var bindingFilter = new OptimizeRequestContextBindingFilter();
    final var componentAccessFilter =
        new OptimizeComponentAccessFilter(
            new OptimizeWebAppProviderAdapter(),
            authorizationCheckPort,
            new OptimizeWebAppAccessDeniedAdapter(),
            authenticationProvider);
    return http -> {
      http.addFilterBefore(bindingFilter, AuthorizationFilter.class);
      http.addFilterAfter(componentAccessFilter, AuthorizationFilter.class);
    };
  }
}
