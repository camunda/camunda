/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.handler;

import io.camunda.auth.spring.utils.RequestValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@link OidcClientInitiatedLogoutSuccessHandler} customization that stores a validated {@code
 * Referer} header as the post-logout redirect URI and propagates the OIDC user claim {@code
 * login_hint} as a {@code logout_hint} query parameter to the provider's end-session endpoint.
 */
public class CamundaOidcLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {

  public static final String POST_LOGOUT_REDIRECT_ATTRIBUTE = "postLogoutRedirect";
  private static final Logger LOG = LoggerFactory.getLogger(CamundaOidcLogoutSuccessHandler.class);
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

    final String referer = request.getHeader("referer");
    if (RequestValidationUtils.isAllowedRedirect(request, referer)) {
      request.getSession().setAttribute(POST_LOGOUT_REDIRECT_ATTRIBUTE, referer);
    }

    final String baseLogoutUrl = super.determineTargetUrl(request, response, authentication);

    if (Objects.equals(baseLogoutUrl, getDefaultTargetUrl())) {
      LOG.trace(
          "Unable to determine end-session endpoint for OIDC logout. "
              + "Falling back to '{}' without logout hint.",
          baseLogoutUrl);
      return baseLogoutUrl;
    }

    if (!(authentication instanceof final OAuth2AuthenticationToken oauth)) {
      LOG.trace(
          "Authentication is not of type OAuth2AuthenticationToken: '{}'. "
              + "Falling back to '{}' without logout hint.",
          authentication,
          baseLogoutUrl);
      return baseLogoutUrl;
    }

    final String registrationId = oauth.getAuthorizedClientRegistrationId();
    final ClientRegistration clientRegistration =
        clientRegistrationRepository.findByRegistrationId(registrationId);

    if (clientRegistration == null) {
      LOG.trace(
          "No client registration found for id '{}'. "
              + "Falling back to '{}' without logout hint.",
          registrationId,
          baseLogoutUrl);
      return baseLogoutUrl;
    }

    if (!(oauth.getPrincipal() instanceof final OidcUser oidcUser)) {
      LOG.trace(
          "Principal is not of type OidcUser: '{}'. " + "Falling back to '{}' without logout hint.",
          oauth.getPrincipal(),
          baseLogoutUrl);
      return baseLogoutUrl;
    }

    final String logoutHint = oidcUser.getClaim("login_hint");
    if (logoutHint == null) {
      LOG.trace(
          "No 'login_hint' claim found in OIDC user. "
              + "Falling back to '{}' without logout hint.",
          baseLogoutUrl);
      return baseLogoutUrl;
    }

    return UriComponentsBuilder.fromUriString(baseLogoutUrl)
        .queryParam("logout_hint", logoutHint)
        .build()
        .toUriString();
  }
}
