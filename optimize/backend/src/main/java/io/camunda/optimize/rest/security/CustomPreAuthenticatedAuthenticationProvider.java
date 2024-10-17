/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CustomPreAuthenticatedAuthenticationProvider implements AuthenticationProvider {

  public CustomPreAuthenticatedAuthenticationProvider() {}

  @Override
  public Authentication authenticate(final Authentication authentication)
      throws AuthenticationException {
    if (authentication instanceof final PreAuthenticatedAuthenticationToken token
        && authentication.getPrincipal() != null) {
      token.setAuthenticated(true);
      return token;
    }
    return null;
  }

  @Override
  public boolean supports(final Class<?> authentication) {
    return authentication.equals(PreAuthenticatedAuthenticationToken.class);
  }
}
