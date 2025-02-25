/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

public class SecurityConfiguration {

  private AuthenticationConfiguration authentication = new AuthenticationConfiguration();
  private AuthorizationsConfiguration authorizations = new AuthorizationsConfiguration();
  private InitializationConfiguration initialization = new InitializationConfiguration();
  private MultiTenancyConfiguration multiTenancy = new MultiTenancyConfiguration();

  public AuthenticationConfiguration getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final AuthenticationConfiguration authentication) {
    this.authentication = authentication;
  }

  public AuthorizationsConfiguration getAuthorizations() {
    return authorizations;
  }

  public void setAuthorizations(final AuthorizationsConfiguration authorizations) {
    this.authorizations = authorizations;
  }

  public InitializationConfiguration getInitialization() {
    return initialization;
  }

  public void setInitialization(final InitializationConfiguration initialization) {
    this.initialization = initialization;
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
}
