/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.engine;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BasicAccessAuthenticationFilter implements ClientRequestFilter {
  private String defaultEngineAuthenticationUser;
  private String defaultEngineAuthenticationPassword;

  public BasicAccessAuthenticationFilter(String defaultEngineAuthenticationUser,
                                         String defaultEngineAuthenticationPassword) {
    this.defaultEngineAuthenticationUser = defaultEngineAuthenticationUser;
    this.defaultEngineAuthenticationPassword = defaultEngineAuthenticationPassword;
  }

  public void filter(ClientRequestContext requestContext) {
    MultivaluedMap<String, Object> headers = requestContext.getHeaders();
    final String basicAuthentication = getBasicAuthentication();
    headers.add("Authorization", basicAuthentication);
  }

  private String getBasicAuthentication() {
    String token = defaultEngineAuthenticationUser + ":" + defaultEngineAuthenticationPassword;
    return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
  }
}

