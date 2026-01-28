/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

public class ExternalClientRedirectLoginSuccessHandler
    extends SavedRequestAwareAuthenticationSuccessHandler {

  private static final String MODELER_URL_PROTOCOL = "camunda-modeler://";

  private RequestCache requestCache;
  private OAuth2AuthorizedClientRepository authorizedClientRepository;
  private String originalRedirectParameter;

  public ExternalClientRedirectLoginSuccessHandler(
      RequestCache requestCache, OAuth2AuthorizedClientRepository authorizedClientRepository) {
    this.requestCache = requestCache;
    this.authorizedClientRepository = authorizedClientRepository;
    this.originalRedirectParameter = "loginSuccessRedirect";
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws ServletException, IOException {

    final SavedRequest savedRequest = requestCache.getRequest(request, response);
    if (savedRequest == null) {
      super.onAuthenticationSuccess(request, response, authentication);
    }

    final String[] redirectParameterValues =
        savedRequest.getParameterValues(originalRedirectParameter);
    if (redirectParameterValues.length != 1) {
      super.onAuthenticationSuccess(request, response, authentication);
    }

    final String originalRedirectUrl = redirectParameterValues[0];
    final String redirectUrl =
        appendTokenToRedirectUrl(request, authentication, originalRedirectUrl);

    requestCache.removeRequest(request, response);

    getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    clearAuthenticationAttributes(request);
  }

  private String appendTokenToRedirectUrl(
      HttpServletRequest request, Authentication authentication, final String originalRedirectUrl) {
    if (originalRedirectUrl.startsWith(MODELER_URL_PROTOCOL)
        && authentication instanceof OAuth2AuthenticationToken) {
      final OAuth2AuthenticationToken jwtAuthentication =
          (OAuth2AuthenticationToken) authentication;
      final String authorizedClientRegistrationId =
          jwtAuthentication.getAuthorizedClientRegistrationId();

      final OAuth2AuthorizedClient authorizedClient =
          authorizedClientRepository.loadAuthorizedClient(
              authorizedClientRegistrationId, authentication, request);
      final OAuth2AccessToken accessToken = authorizedClient.getAccessToken();

      return originalRedirectUrl + "?token=" + accessToken.getTokenValue();
    } else {
      return originalRedirectUrl;
    }
  }
}
