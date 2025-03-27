/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.CamundaJwtUser;
import io.camunda.security.entity.AuthenticationMethod;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class CamundaJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
  final CamundaOAuthPrincipalService camundaOAuthPrincipalService;

  public CamundaJwtAuthenticationConverter(
      final CamundaOAuthPrincipalService camundaOAuthPrincipalService) {
    this.camundaOAuthPrincipalService = camundaOAuthPrincipalService;
  }

  @Override
  public AbstractAuthenticationToken convert(final Jwt source) {
    final AbstractAuthenticationToken token = jwtAuthenticationConverter.convert(source);
    return new CamundaJwtAuthenticationToken(
        source,
        new CamundaJwtUser(
            source, camundaOAuthPrincipalService.loadOAuthContext(source.getClaims())),
        token.getCredentials(),
        token.getAuthorities());
  }
}
