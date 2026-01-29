/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ClientLoginController {

  private static final String MODELER_URL_PROTOCOL = "camunda-modeler://";

  public OAuth2AuthorizedClientRepository authorizedClientRepository;

  public ClientLoginController(OAuth2AuthorizedClientRepository authorizedClientRepository) {
    this.authorizedClientRepository = authorizedClientRepository;
  }

  @GetMapping("/clientlogin")
  public String redirectBackWithToken(
      HttpServletRequest request,
      Authentication authentication,
      @RequestParam("loginSuccessRedirect") String loginSuccessRedirect) {

    return "redirect:" + appendTokenToRedirectUrl(request, authentication, loginSuccessRedirect);
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

      final String appendingCharacter;
      if (originalRedirectUrl.contains("?")) {
        appendingCharacter = "&";
      } else {
        appendingCharacter = "?";
      }

      return originalRedirectUrl + appendingCharacter + "token=" + accessToken.getTokenValue();
    } else {
      return originalRedirectUrl;
    }
  }
}
