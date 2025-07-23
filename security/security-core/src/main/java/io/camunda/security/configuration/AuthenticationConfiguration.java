/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.security.entity.AuthenticationMethod;

public class AuthenticationConfiguration {
  public static final AuthenticationMethod DEFAULT_METHOD = AuthenticationMethod.BASIC;
  public static final boolean DEFAULT_UNPROTECTED_API = false;
  public static final boolean DEFAULT_UNPROTECTED_MCP = false;

  private AuthenticationMethod method = DEFAULT_METHOD;
  private OidcAuthenticationConfiguration oidcAuthenticationConfiguration =
      new OidcAuthenticationConfiguration();
  private boolean unprotectedApi = DEFAULT_UNPROTECTED_API;
  private boolean unprotectedMcp = DEFAULT_UNPROTECTED_MCP;

  public boolean getUnprotectedApi() {
    return unprotectedApi;
  }

  public boolean getUnprotectedMcp() {
    return unprotectedMcp;
  }

  public void setUnprotectedApi(final boolean value) {
    unprotectedApi = value;
  }

  public void setUnprotectedMcp(final boolean value) {
    unprotectedMcp = value;
  }

  public AuthenticationMethod getMethod() {
    return method;
  }

  public void setMethod(final AuthenticationMethod method) {
    this.method = method;
  }

  public OidcAuthenticationConfiguration getOidc() {
    return oidcAuthenticationConfiguration;
  }

  public void setOidc(final OidcAuthenticationConfiguration configuration) {
    oidcAuthenticationConfiguration = configuration;
  }
}
