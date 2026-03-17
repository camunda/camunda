/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import java.time.Duration;

public class SecurityConfigurations {

  public static SecurityConfiguration unauthenticatedAndUnauthorized() {
    final SecurityConfiguration securityConfiguration = new SecurityConfiguration();
    securityConfiguration.getAuthorizations().setEnabled(false);
    return securityConfiguration;
  }

  /**
   * Creates a default {@link AuthenticationConfig} for test contexts where {@link
   * SecurityConfiguration} no longer carries authentication fields. Returns a BASIC auth config
   * with unprotected API enabled.
   */
  public static AuthenticationConfig toAuthenticationConfig(
      final SecurityConfiguration securityConfiguration) {
    return new AuthenticationConfig(AuthenticationMethod.BASIC, Duration.ofSeconds(30), true, null);
  }
}
