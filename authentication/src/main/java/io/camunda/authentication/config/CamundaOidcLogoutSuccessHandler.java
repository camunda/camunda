/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static io.camunda.authentication.controller.PostLogoutController.POST_LOGOUT_REDIRECT_ATTRIBUTE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.util.UrlUtils;
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

    final String referer = request.getHeader("referer");
    if (isAllowedRedirect(request, referer)) {
      request.getSession().setAttribute(POST_LOGOUT_REDIRECT_ATTRIBUTE, referer);
    }

    final String baseLogoutUrl = super.determineTargetUrl(request, response, authentication);

    // Break early if logout URL can't be constructed.
    // Usually means IdP didn't provide end session endpoint in its metadata.
    if (Objects.equals(baseLogoutUrl, getDefaultTargetUrl())) {
      return baseLogoutUrl;
    }

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

  private boolean isAllowedRedirect(final HttpServletRequest request, final String url) {
    if (url == null) {
      return false;
    }
    final String baseUrl =
        UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request))
            .replacePath(request.getContextPath())
            .replaceQuery(null)
            .fragment(null)
            .build()
            .toUriString();
    return url.startsWith(baseUrl);
  }
}
