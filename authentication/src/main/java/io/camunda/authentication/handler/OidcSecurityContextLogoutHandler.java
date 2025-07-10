/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import io.camunda.authentication.config.OidcClientRegistration;
import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.util.UriComponentsBuilder;

public class OidcSecurityContextLogoutHandler implements LogoutHandler {

  private static final Logger LOG = LoggerFactory.getLogger(OidcSecurityContextLogoutHandler.class);

  private final ClientRegistrationRepository clientRegistrationRepository;
  private final OAuth2AuthorizedClientService authorizedClientService;
  private final SecurityConfiguration securityConfiguration;

  public OidcSecurityContextLogoutHandler(
      final ClientRegistrationRepository clientRegistrationRepository,
      final OAuth2AuthorizedClientService authorizedClientService,
      final SecurityConfiguration securityConfiguration) {
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.authorizedClientService = authorizedClientService;
    this.securityConfiguration = securityConfiguration;
  }

  @Override
  public void logout(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {

    if (authentication == null) {
      return;
    }

    storeLogoutRedirectUrl(request, authentication);
    clearAuthorizedClients(authentication);
    final HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
  }

  private void clearAuthorizedClients(final Authentication authentication) {
    if (authentication instanceof OAuth2AuthenticationToken) {
      final OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
      final String registrationId = oauth2Token.getAuthorizedClientRegistrationId();
      final String principalName = authentication.getName();
      authorizedClientService.removeAuthorizedClient(registrationId, principalName);
    }
  }

  private void storeLogoutRedirectUrl(
      final HttpServletRequest request, final Authentication authentication) {
    final String logoutRedirectUrl = buildOidcLogoutUrl();
    if (logoutRedirectUrl != null) {
      // store in request attribute for LogoutSuccessHandler
      request.setAttribute(
          OidcLogoutSuccessHandler.LOGOUT_SUCCESS_REDIRECT_URL_PROPERTY, logoutRedirectUrl);
    }
  }

  private String buildOidcLogoutUrl() {
    try {
      final ClientRegistration clientRegistration =
          clientRegistrationRepository.findByRegistrationId(OidcClientRegistration.REGISTRATION_ID);

      if (clientRegistration == null) {
        return null;
      }

      final String endSessionEndpoint =
          securityConfiguration.getAuthentication().getOidc().getIssuerLogoutUrl();

      if (endSessionEndpoint == null) {
        return null;
      }

      final UriComponentsBuilder logoutUriBuilder =
          UriComponentsBuilder.fromUriString(endSessionEndpoint)
              .queryParam(OAuth2ParameterNames.CLIENT_ID, clientRegistration.getClientId());

      return logoutUriBuilder.build().toString();

    } catch (final Exception e) {
      LOG.warn("Error building OIDC logout url", e);
      return null;
    }
  }
}
