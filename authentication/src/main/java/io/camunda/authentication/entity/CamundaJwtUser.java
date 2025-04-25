/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.entity;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

public class CamundaJwtUser implements CamundaOAuthPrincipal, Serializable {
  private final Jwt jwt;
  private final OAuthContext oauthContext;

  public CamundaJwtUser(
      final Jwt jwt, final Set<String> mappingIds, final AuthenticationContext authentication) {
    this.jwt = jwt;
    oauthContext = new OAuthContext(new HashSet<>(), mappingIds, authentication);
  }

  public CamundaJwtUser(final Jwt jwt, final OAuthContext oauthContext) {
    this.jwt = jwt;
    this.oauthContext = oauthContext;
  }

  @Override
  public String getEmail() {
    return jwt.getClaimAsString("email");
  }

  @Override
  public String getDisplayName() {
    return jwt.getClaimAsString("displayName");
  }

  @Override
  public AuthenticationContext getAuthenticationContext() {
    return oauthContext.authenticationContext();
  }

  @Override
  public OAuthContext getOAuthContext() {
    return oauthContext;
  }

  @Override
  public Map<String, Object> getClaims() {
    return jwt.getClaims();
  }
}
