/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.oauth;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;

import io.camunda.identity.sdk.Identity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityJwt2AuthenticationTokenConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  @Autowired private Identity identity;

  @Override
  public AbstractAuthenticationToken convert(final Jwt jwt) {
    // this will validate audience
    try {
      identity.authentication().verifyToken(jwt.getTokenValue());
      return new IdentityTenantAwareJwtAuthenticationToken(jwt, null, jwt.getSubject());
    } catch (Exception e) {
      throw new InsufficientAuthenticationException(e.getMessage());
    }
  }
}
