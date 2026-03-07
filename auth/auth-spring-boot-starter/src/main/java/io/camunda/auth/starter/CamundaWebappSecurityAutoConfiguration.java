/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.spring.SecurityFilterChainCustomizer;
import io.camunda.auth.spring.SecurityFilterChainHelper;
import io.camunda.auth.spring.controller.PostLogoutController;
import io.camunda.auth.spring.filter.OAuth2RefreshTokenFilter;
import io.camunda.auth.spring.handler.AuthFailureHandler;
import io.camunda.auth.spring.handler.CamundaOidcLogoutSuccessHandler;
import io.camunda.auth.spring.handler.WebappRedirectStrategy;
import io.camunda.auth.spring.oidc.OidcTokenEndpointCustomizer;
import io.camunda.auth.starter.config.CamundaAuthProperties;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Auto-configuration for webapp SecurityFilterChain with OIDC login/logout. Gated on {@code
 * camunda.auth.security.webapp-enabled=true}. Provides the webapp filter chain with extension point
 * via {@link SecurityFilterChainCustomizer}.
 */
@AutoConfiguration(after = CamundaSecurityFilterChainAutoConfiguration.class)
@ConditionalOnProperty(name = "camunda.auth.security.webapp-enabled", havingValue = "true")
@Import(PostLogoutController.class)
public class CamundaWebappSecurityAutoConfiguration {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaWebappSecurityAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public WebappRedirectStrategy webappRedirectStrategy() {
    return new WebappRedirectStrategy();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      name = "camunda.auth.security.idp-logout-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CamundaOidcLogoutSuccessHandler camundaOidcLogoutSuccessHandler(
      final ClientRegistrationRepository clientRegistrationRepository,
      final CamundaAuthProperties properties) {
    final var handler = new CamundaOidcLogoutSuccessHandler(clientRegistrationRepository);
    handler.setPostLogoutRedirectUri("{baseUrl}/post-logout");
    handler.setRedirectStrategy(new WebappRedirectStrategy());
    return handler;
  }

  @Bean
  @Order(1)
  @ConditionalOnMissingBean(name = "oidcWebappSecurityFilterChain")
  public SecurityFilterChain oidcWebappSecurityFilterChain(
      final HttpSecurity http,
      final CamundaAuthProperties properties,
      final JwtDecoder jwtDecoder,
      final OidcUserService oidcUserService,
      final AuthFailureHandler authFailureHandler,
      final OAuth2RefreshTokenFilter oAuth2RefreshTokenFilter,
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OAuth2AuthorizationRequestResolver authorizationRequestResolver,
      final OidcTokenEndpointCustomizer tokenEndpointCustomizer,
      final CamundaOidcLogoutSuccessHandler logoutSuccessHandler,
      final List<SecurityFilterChainCustomizer> customizers)
      throws Exception {

    final var securityProps = properties.getSecurity();
    final var webappPaths = securityProps.getWebappPaths();
    final var sessionCookie = securityProps.getSessionCookie();
    final var csrfTokenName = securityProps.getCsrfTokenName();

    LOG.debug("Configuring OIDC webapp security for paths: {}", webappPaths);

    http.securityMatcher(webappPaths.toArray(String[]::new));

    // OIDC login
    http.oauth2Login(
        login ->
            login
                .authorizationEndpoint(
                    authz -> authz.authorizationRequestResolver(authorizationRequestResolver))
                .tokenEndpoint(tokenEndpointCustomizer)
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                .failureHandler(authFailureHandler));

    // OIDC logout
    http.logout(
        logout ->
            logout
                .logoutSuccessHandler(logoutSuccessHandler)
                .deleteCookies(sessionCookie, csrfTokenName));

    // OAuth2 resource server for API calls within webapp paths
    http.oauth2ResourceServer(
        rs ->
            rs.jwt(jwt -> jwt.decoder(jwtDecoder))
                .authenticationEntryPoint(authFailureHandler)
                .accessDeniedHandler(authFailureHandler));

    // Refresh token filter
    http.addFilterAfter(oAuth2RefreshTokenFilter, BasicAuthenticationFilter.class);

    // CSRF
    if (securityProps.isCsrfEnabled()) {
      SecurityFilterChainHelper.configureCsrf(
          http,
          securityProps.getCsrfTokenName(),
          securityProps.getUnprotectedPaths(),
          securityProps.getUnprotectedApiPaths(),
          "/login",
          "/logout");
    } else {
      http.csrf(csrf -> csrf.disable());
    }

    // Secure headers
    SecurityFilterChainHelper.setupSecureHeaders(http);

    // Authorization
    http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());

    // Exception handling — do NOT override the authenticationEntryPoint here;
    // oauth2Login() sets up a redirect to the IdP for unauthenticated requests.
    http.exceptionHandling(eh -> eh.accessDeniedHandler(authFailureHandler));

    // Apply repo-specific customizers
    for (final SecurityFilterChainCustomizer customizer :
        customizers != null
            ? customizers
            : Collections.<SecurityFilterChainCustomizer>emptyList()) {
      customizer.customize(http);
    }

    return http.build();
  }
}
