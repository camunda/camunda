/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

public class OAuth2AuthenticationExceptionHandler implements AuthenticationFailureHandler {

  private static final Logger LOG =
      LoggerFactory.getLogger(OAuth2AuthenticationExceptionHandler.class);

  @Override
  public void onAuthenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException exception)
      throws IOException, ServletException {
    if (exception instanceof final OAuth2AuthenticationException oauthEx) {
      if ("authorization_request_not_found".equals(oauthEx.getError().getErrorCode())) {
        LOG.debug("Authorization request not found, redirecting to /");
        response.sendRedirect("/");
        return;
      }
    }
    throw exception;
  }
}
