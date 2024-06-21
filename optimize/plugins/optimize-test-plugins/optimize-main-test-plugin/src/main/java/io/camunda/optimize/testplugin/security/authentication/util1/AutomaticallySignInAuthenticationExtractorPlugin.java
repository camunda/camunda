/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.security.authentication.util1;

import io.camunda.optimize.plugin.security.authentication.AuthenticationExtractor;
import io.camunda.optimize.plugin.security.authentication.AuthenticationResult;
import jakarta.servlet.http.HttpServletRequest;

public class AutomaticallySignInAuthenticationExtractorPlugin implements AuthenticationExtractor {

  public static final String CUSTOM_AUTH_HEADER = "user";

  @Override
  public AuthenticationResult extractAuthenticatedUser(final HttpServletRequest servletRequest) {
    final String userToAuthenticate = servletRequest.getHeader(CUSTOM_AUTH_HEADER);

    final AuthenticationResult result = new AuthenticationResult();
    result.setAuthenticatedUser(userToAuthenticate);
    result.setAuthenticated(userToAuthenticate != null);
    return result;
  }
}
