/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

/**
 * Handles OIDC login failures. {@code authorization_request_not_found} redirects to the application
 * root so the user can restart the login flow. Every other failure is answered with a 401.
 *
 * <p>Rethrowing a failure instead would surface it as a 500 whose detail is the exception message.
 * For a rejected ID token that message carries the token's claim values, such as the user's
 * organization ids.
 */
public class OAuth2AuthenticationExceptionHandler implements AuthenticationFailureHandler {
  public static final String AUTHORIZATION_REQUEST_NOT_FOUND_ERROR_CODE =
      "authorization_request_not_found";

  private static final Logger LOG =
      LoggerFactory.getLogger(OAuth2AuthenticationExceptionHandler.class);
  // An error code can come from the IdP's callback query string, so only plain codes are logged.
  private static final Pattern LOGGABLE_ERROR_CODE = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

  private final AuthenticationFailureHandler unauthorizedHandler =
      new SimpleUrlAuthenticationFailureHandler();

  @Override
  public void onAuthenticationFailure(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException exception)
      throws IOException, ServletException {

    if (exception instanceof final OAuth2AuthenticationException e) {
      if (e.getError() != null
          && AUTHORIZATION_REQUEST_NOT_FOUND_ERROR_CODE.equals(e.getError().getErrorCode())) {
        response.sendRedirect("/");
        return;
      }
    }

    // The code, not the message or description: those can contain the token's claim values.
    LOG.warn("OIDC login failed with error '{}'; responding with 401", errorCode(exception));
    unauthorizedHandler.onAuthenticationFailure(request, response, exception);
  }

  static String errorCode(final AuthenticationException exception) {
    if (exception instanceof final OAuth2AuthenticationException e && e.getError() != null) {
      final String code = e.getError().getErrorCode();
      return code != null && LOGGABLE_ERROR_CODE.matcher(code).matches() ? code : "unknown";
    }
    return exception.getClass().getSimpleName();
  }
}
