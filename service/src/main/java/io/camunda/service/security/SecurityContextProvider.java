/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.security;

import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.auth.SecurityContext.Builder;
import io.camunda.security.configuration.SecurityConfiguration;

public class SecurityContextProvider {

  private final SecurityConfiguration securityConfiguration;

  public SecurityContextProvider(final SecurityConfiguration securityConfiguration) {
    this.securityConfiguration = securityConfiguration;
  }

  public SecurityContext provideSecurityContext(
      final Authentication authentication, final Authorization authorization) {
    final SecurityContext.Builder securityContextbuilder =
        new Builder().withAuthentication(authentication);
    if (securityConfiguration.getAuthorizations().isEnabled()) {
      securityContextbuilder.withAuthorization(authorization);
    }
    return securityContextbuilder.build();
  }

  public SecurityContext provideSecurityContext(final Authentication authentication) {
    return provideSecurityContext(authentication, null);
  }
}
