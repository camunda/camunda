/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.CamundaJwtUser;
import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

public class CamundaJwtAuthenticationToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {

  public CamundaJwtAuthenticationToken(
      final Jwt token,
      final CamundaJwtUser principal,
      final Object credentials,
      final Collection<? extends GrantedAuthority> authorities) {
    super(token, principal, credentials, authorities);
    setAuthenticated(true);
  }

  @Override
  public Map<String, Object> getTokenAttributes() {
    return getToken().getClaims();
  }
}
