/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.autoconfigure;

import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.spi.CamundaAuthenticationConverter;
import io.camunda.gatekeeper.spring.condition.ConditionalOnAuthenticationMethod;
import io.camunda.gatekeeper.spring.condition.ConditionalOnUnprotectedApi;
import io.camunda.gatekeeper.spring.controller.PostLogoutController;
import io.camunda.gatekeeper.spring.converter.UnprotectedCamundaAuthenticationConverter;
import io.camunda.gatekeeper.spring.handler.CamundaOidcLogoutSuccessHandler;
import io.camunda.gatekeeper.spring.handler.WebappRedirectStrategy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/**
 * Auto-configuration for webapp-specific filters, converters, and handlers. Provides beans for
 * admin user checks, OIDC logout, unprotected API converters, and the post-logout controller.
 */
@AutoConfiguration(
    after = {GatekeeperAuthAutoConfiguration.class, GatekeeperOidcAutoConfiguration.class})
public final class GatekeeperWebappFiltersAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "unprotectedCamundaAuthenticationConverter")
  @ConditionalOnUnprotectedApi
  public CamundaAuthenticationConverter<Authentication>
      unprotectedCamundaAuthenticationConverter() {
    return new UnprotectedCamundaAuthenticationConverter();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  public WebappRedirectStrategy webappRedirectStrategy() {
    return new WebappRedirectStrategy();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  public LogoutSuccessHandler camundaOidcLogoutSuccessHandler(
      final ClientRegistrationRepository clientRegistrationRepository,
      final WebappRedirectStrategy redirectStrategy,
      final AuthenticationConfig authenticationConfig) {
    if (!authenticationConfig.oidc().idpLogoutEnabled()) {
      // Return a no-content handler when IdP logout is disabled
      return (request, response, authentication) ->
          response.setStatus(org.springframework.http.HttpStatus.NO_CONTENT.value());
    }

    final var handler = new CamundaOidcLogoutSuccessHandler(clientRegistrationRepository);
    handler.setPostLogoutRedirectUri("{baseUrl}/post-logout");
    handler.setRedirectStrategy(redirectStrategy);
    return handler;
  }

  @Bean
  @ConditionalOnMissingBean
  public PostLogoutController postLogoutController() {
    return new PostLogoutController();
  }
}
