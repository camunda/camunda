/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security;

import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;

import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class CsrfRequireMatcher implements RequestMatcher {
  private static final Pattern ALLOWED_METHODS = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");

  // GraphQL is bypassed as it is deprecated and not used by our Front-end
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

    final String referer = request.getHeader("Referer");

    // If request is from Swagger UI
    final String baseRequestUrl;
    try {
      final URL requestUrl = new URL(request.getRequestURL().toString());
      baseRequestUrl =
          requestUrl.getProtocol()
              + "://"
              + requestUrl.getHost()
              + (requestUrl.getPort() > 0 ? ":" + requestUrl.getPort() : "");
    } catch (final MalformedURLException e) {
      throw new RuntimeException(e);
    }

    if (referer != null && referer.matches(baseRequestUrl + "/swagger-ui.*")) {
      return false;
    }

    // If is authenticated from as API user using Bareer Token
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
