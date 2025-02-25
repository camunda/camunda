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

  private AuthenticationMethod method = DEFAULT_METHOD;
  private BasicAuthenticationConfiguration basicAuthenticationConfiguration =
      new BasicAuthenticationConfiguration();
  private OidcAuthenticationConfiguration oidcAuthenticationConfiguration =
      new OidcAuthenticationConfiguration();

  public AuthenticationMethod getMethod() {
    return method;
  }

  public void setMethod(final AuthenticationMethod method) {
    this.method = method;
  }

  public BasicAuthenticationConfiguration getBasic() {
    return basicAuthenticationConfiguration;
  }

  public void setBasic(final BasicAuthenticationConfiguration configuration) {
    basicAuthenticationConfiguration = configuration;
  }

  public OidcAuthenticationConfiguration getOidc() {
    return oidcAuthenticationConfiguration;
  }

  public void setOidc(final OidcAuthenticationConfiguration configuration) {
    oidcAuthenticationConfiguration = configuration;
  }
}
