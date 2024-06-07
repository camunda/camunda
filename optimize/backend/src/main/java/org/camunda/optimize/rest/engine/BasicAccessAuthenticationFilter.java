/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.engine;

import static jakarta.ws.rs.Priorities.AUTHENTICATION;

import jakarta.annotation.Priority;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Priority(AUTHENTICATION)
public class BasicAccessAuthenticationFilter implements ClientRequestFilter {
  private final String defaultEngineAuthenticationUser;
  private final String defaultEngineAuthenticationPassword;

  public BasicAccessAuthenticationFilter(
      final String defaultEngineAuthenticationUser,
      final String defaultEngineAuthenticationPassword) {
    this.defaultEngineAuthenticationUser = defaultEngineAuthenticationUser;
    this.defaultEngineAuthenticationPassword = defaultEngineAuthenticationPassword;
  }

  @Override
  public void filter(final ClientRequestContext requestContext) {
    final MultivaluedMap<String, Object> headers = requestContext.getHeaders();
    final String basicAuthentication = getBasicAuthentication();
    headers.add("Authorization", basicAuthentication);
  }

  private String getBasicAuthentication() {
    final String token =
        defaultEngineAuthenticationUser + ":" + defaultEngineAuthenticationPassword;
    return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
  }
}
