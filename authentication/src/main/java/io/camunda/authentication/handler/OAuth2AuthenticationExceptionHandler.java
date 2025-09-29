/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

public class OAuth2AuthenticationExceptionHandler implements AuthenticationFailureHandler {
  public static final String AUTHORIZATION_REQUEST_NOT_FOUND_ERROR_CODE =
      "authorization_request_not_found";

  @Override
  public void onAuthenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException exception)
      throws IOException {

    if (exception instanceof final OAuth2AuthenticationException e) {
      if (e.getError() != null
          && AUTHORIZATION_REQUEST_NOT_FOUND_ERROR_CODE.equals(e.getError().getErrorCode())) {
        response.sendRedirect("/");
        return;
      }
    }

    throw exception;
  }
}
