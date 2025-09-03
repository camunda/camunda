/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.util.Map;

public class ProvidersConfiguration {

  private Map<String, OidcAuthenticationConfiguration> oidc;

  public Map<String, OidcAuthenticationConfiguration> getOidc() {
    return oidc;
  }

  public void setOidc(final Map<String, OidcAuthenticationConfiguration> oidc) {
    this.oidc = oidc;
  }
}
