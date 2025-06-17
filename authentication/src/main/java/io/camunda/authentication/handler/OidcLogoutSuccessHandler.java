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
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

public class OidcLogoutSuccessHandler implements LogoutSuccessHandler {

  public static final String LOGOUT_SUCCESS_REDIRECT_URL_PROPERTY = "camunda.logout.redirect.url";

  @Override
  public void onLogoutSuccess(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication)
      throws IOException {
    final String oidcLogoutUrl =
        (String) request.getAttribute(LOGOUT_SUCCESS_REDIRECT_URL_PROPERTY);
    response.sendRedirect(Objects.requireNonNullElse(oidcLogoutUrl, "/"));
  }
}
