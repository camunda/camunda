/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.security;

import io.camunda.identity.sdk.authentication.AccessToken;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public record IdentityAuthentication(AccessToken token, List<String> tenantIds)
    implements Authentication {

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.emptyList();
  }

  @Override
  public Object getCredentials() {
    return token.getToken();
  }

  @Override
  public Object getDetails() {
    return token.getUserDetails();
  }

  @Override
  public Object getPrincipal() {
    return token;
  }

  @Override
  public boolean isAuthenticated() {
    return true;
  }

  @Override
  public void setAuthenticated(final boolean isAuthenticated) throws IllegalArgumentException {
    if (!isAuthenticated) {
      throw new UnsupportedOperationException(
          "Expected to mark Identity token as unauthenticated, but it always is");
    }
  }

  @Override
  public String getName() {
    return token.getUserDetails().getName().orElse(token.getUserDetails().getId());
  }
}
