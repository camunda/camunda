/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class CsrfRequireMatcher implements RequestMatcher {
  private static final Pattern ALLOWED_METHODS = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");

  private static final Pattern ALLOWED_PATHS =
      Pattern.compile(LOGIN_RESOURCE + "|" + LOGOUT_RESOURCE);

  @Override
  public boolean matches(final HttpServletRequest request) {
    // If request is from Allowed Methods, Login and Logout
    if (ALLOWED_METHODS.matcher(request.getMethod()).matches()) {
      return false;
    }
    if (ALLOWED_PATHS.matcher(request.getServletPath()).matches()) {
      return false;
    }

    // Check if the user is authenticated
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    final boolean isUserAuthenticated = authentication != null && authentication.isAuthenticated();

    if (!isUserAuthenticated) {
      return false;
    }

    final String referer = request.getHeader("Referer");

    // If request is from Swagger UI
    if (referer != null && referer.matches(".*/swagger-ui.*")) {
      return false;
    }

    // If is authenticated from as API user using Barer Token
    final String authorizationHeader = request.getHeader("Authorization");
    final boolean isAuthorizationHeaderPresent =
        authorizationHeader != null && authorizationHeader.startsWith("Bearer ");
    if (isAuthorizationHeaderPresent) {
      return false;
    }

    // otherwise, CSRF is required
    return true;
  }
}
