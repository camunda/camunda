/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.rest.security.csl.OptimizeWebAppProviderAdapter.OPTIMIZE_WEB_APP;

import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.core.port.out.AuthorizedComponentsPort;
import io.camunda.security.spring.filter.WebAppAuthorizationCheckFilter;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import io.camunda.security.spring.spi.WebAppAccessDeniedHandlerPort;
import io.camunda.security.spring.spi.WebAppProviderPort;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Enforces access to the Optimize component through CSL's own component authorization, driven by
 * the edition's {@link OptimizeComponentAccessPolicy}. Two enforcement points, one policy:
 *
 * <ul>
 *   <li>the OIDC callback, through {@link OptimizeComponentAccessOidcUserService}, so a user who
 *       may not access Optimize cannot complete the login
 *   <li>every request of an established session, through CSL's {@link
 *       WebAppAuthorizationCheckFilter}, so access ends when the grant is revoked
 * </ul>
 *
 * <p>CSL adds its filter to the webapp chain only. Optimize's single page app authenticates its
 * calls with the session cookie, and those calls are served by the API chain, so the filter is
 * added there as well. Bearer requests stay unaffected, because {@link
 * OptimizeWebAppProviderAdapter} claims login sessions only.
 */
@Configuration
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnBean(OptimizeComponentAccessPolicy.class)
public class OptimizeComponentAccessConfiguration {

  /** Installed by CSL on the webapp login, see {@link OptimizeComponentAccessOidcUserService}. */
  @Bean
  public OidcUserService componentAccessOidcUserService(
      final OptimizeComponentAccessPolicy policy) {
    return new OptimizeComponentAccessOidcUserService(policy);
  }

  @Bean
  public WebAppProviderPort webAppProviderPort() {
    return new OptimizeWebAppProviderAdapter();
  }

  @Bean
  public AuthorizationCheckPort authorizationCheckPort(final OptimizeComponentAccessPolicy policy) {
    return new OptimizeComponentAuthorizationAdapter(policy);
  }

  @Bean
  public WebAppAccessDeniedHandlerPort webAppAccessDeniedHandlerPort() {
    return new OptimizeWebAppAccessDeniedAdapter();
  }

  /** Feeds {@code authorizedComponents} of the current user reported by {@code /me}. */
  @Bean
  public AuthorizedComponentsPort authorizedComponentsPort(
      final OptimizeComponentAccessPolicy policy) {
    return authentication ->
        policy.sessionDenialReason(authentication).isEmpty()
            ? List.of(OPTIMIZE_WEB_APP)
            : List.of();
  }

  /**
   * Extends CSL's webapp authorization filter to the chains it does not install itself, so a
   * session-authenticated API call is checked as well. The filter is a {@code
   * OncePerRequestFilter}, so the webapp chain, where CSL already added it, keeps running it once.
   */
  @Bean
  public SecurityHeadersCustomizer componentAccessFilterOnApiChains(
      final ObjectProvider<WebAppAuthorizationCheckFilter> filterProvider) {
    return http ->
        filterProvider.ifAvailable(
            filter -> http.addFilterAfter(filter, AuthorizationFilter.class));
  }
}
