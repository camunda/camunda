/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.security.authentication.util1;


import org.camunda.optimize.plugin.security.authentication.AuthenticationExtractor;
import org.camunda.optimize.plugin.security.authentication.AuthenticationResult;

import javax.servlet.http.HttpServletRequest;

public class AutomaticallySignInAuthenticationExtractorPlugin implements AuthenticationExtractor {

  public static final String CUSTOM_AUTH_HEADER = "user";

  @Override
  public AuthenticationResult extractAuthenticatedUser(HttpServletRequest servletRequest) {
    String userToAuthenticate = servletRequest.getHeader(CUSTOM_AUTH_HEADER);
    AuthenticationResult result = new AuthenticationResult();
    result.setAuthenticatedUser(userToAuthenticate);
    result.setAuthenticated(userToAuthenticate != null);
    return result;
  }
}
