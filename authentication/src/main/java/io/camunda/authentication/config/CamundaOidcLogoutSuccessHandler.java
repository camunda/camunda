/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.util.UriComponentsBuilder;

public class CamundaOidcLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {

  private final ClientRegistrationRepository clientRegistrationRepository;

  public CamundaOidcLogoutSuccessHandler(
      final ClientRegistrationRepository clientRegistrationRepository) {
    super(clientRegistrationRepository);
    this.clientRegistrationRepository = clientRegistrationRepository;
  }

  @Override
  protected String determineTargetUrl(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {

    final String baseLogoutUrl = super.determineTargetUrl(request, response, authentication);

    if (!(authentication instanceof final OAuth2AuthenticationToken oauth)) {
      return baseLogoutUrl;
    }

    final String registrationId = oauth.getAuthorizedClientRegistrationId();
    final ClientRegistration clientRegistration =
        clientRegistrationRepository.findByRegistrationId(registrationId);

    if (clientRegistration == null) {
      return baseLogoutUrl;
    }

    if (!(oauth.getPrincipal() instanceof final OidcUser oidcUser)) {
      return baseLogoutUrl;
    }

    final String logoutHint = oidcUser.getClaim("login_hint");
    if (logoutHint == null) {
      return baseLogoutUrl;
    }

    return UriComponentsBuilder.fromUriString(baseLogoutUrl)
        .queryParam("logout_hint", logoutHint)
        .build()
        .toUriString();
  }
}
