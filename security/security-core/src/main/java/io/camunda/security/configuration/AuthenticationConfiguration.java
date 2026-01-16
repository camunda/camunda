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
  public static final boolean DEFAULT_LOGOUT_CORS_ENABLED = true;

  private AuthenticationMethod method = DEFAULT_METHOD;
  private String authenticationRefreshInterval = "PT30S";
  private OidcAuthenticationConfiguration oidcAuthenticationConfiguration =
      new OidcAuthenticationConfiguration();
  private boolean unprotectedApi = DEFAULT_UNPROTECTED_API;
  private boolean logoutCorsEnabled = DEFAULT_LOGOUT_CORS_ENABLED;

  private ProvidersConfiguration providers;

  public boolean getUnprotectedApi() {
    return unprotectedApi;
  }

  public void setUnprotectedApi(final boolean value) {
    unprotectedApi = value;
  }

  public boolean getLogoutCorsEnabled() {
    return logoutCorsEnabled;
  }

  public void setLogoutCorsEnabled(final boolean value) {
    logoutCorsEnabled = value;
  }

  public AuthenticationMethod getMethod() {
    return method;
  }

  public void setMethod(final AuthenticationMethod method) {
    this.method = method;
  }

  public String getAuthenticationRefreshInterval() {
    return authenticationRefreshInterval;
  }

  public void setAuthenticationRefreshInterval(final String authenticationRefreshInterval) {
    this.authenticationRefreshInterval = authenticationRefreshInterval;
  }

  public OidcAuthenticationConfiguration getOidc() {
    return oidcAuthenticationConfiguration;
  }

  public void setOidc(final OidcAuthenticationConfiguration configuration) {
    oidcAuthenticationConfiguration = configuration;
  }

  public ProvidersConfiguration getProviders() {
    return providers;
  }

  public void setProviders(final ProvidersConfiguration providers) {
    this.providers = providers;
  }
}
