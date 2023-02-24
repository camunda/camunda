/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security;

import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class CustomPreAuthenticatedAuthenticationProvider implements AuthenticationProvider {

  @Override
  public Authentication authenticate(final Authentication authentication) throws AuthenticationException {
    if (authentication instanceof PreAuthenticatedAuthenticationToken) {
      PreAuthenticatedAuthenticationToken token = (PreAuthenticatedAuthenticationToken) authentication;
      if (authentication.getPrincipal() != null) {
        token.setAuthenticated(true);
        return token;
      }
    }
    return null;
  }

  @Override
  public boolean supports(final Class<?> authentication) {
    return authentication.equals(PreAuthenticatedAuthenticationToken.class);
  }
}
