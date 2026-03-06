/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.config;

public class SecurityConfiguration {
  private AuthenticationConfiguration authentication = new AuthenticationConfiguration();
  private MultiTenancyConfiguration multiTenancy = new MultiTenancyConfiguration();
  private CsrfConfiguration csrf = new CsrfConfiguration();

  public AuthenticationConfiguration getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final AuthenticationConfiguration authentication) {
    this.authentication = authentication;
  }

  public MultiTenancyConfiguration getMultiTenancy() {
    return multiTenancy;
  }

  public void setMultiTenancy(final MultiTenancyConfiguration multiTenancy) {
    this.multiTenancy = multiTenancy;
  }

  public boolean isApiProtected() {
    return !authentication.getUnprotectedApi();
  }

  public CsrfConfiguration getCsrf() {
    return csrf;
  }

  public void setCsrf(final CsrfConfiguration csrf) {
    this.csrf = csrf;
  }
}
