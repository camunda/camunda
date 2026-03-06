/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring;

import io.camunda.auth.domain.model.GrantType;
import io.camunda.auth.domain.model.TokenExchangeRequest;
import io.camunda.auth.domain.model.TokenType;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Converts Spring Security authentication objects to domain {@link TokenExchangeRequest} objects.
 * This is used as a bridge between Spring Security's authentication model and the auth-domain's
 * token exchange model.
 */
public class SpringOidcTokenExchangeConverter {

  /**
   * Converts a Spring JWT authentication into a domain token exchange request.
   *
   * @param authentication the Spring JWT authentication
   * @param targetAudience the target service audience
   * @param scopes the requested scopes
   * @return the domain token exchange request
   */
  public TokenExchangeRequest convert(
      final JwtAuthenticationToken authentication,
      final String targetAudience,
      final Set<String> scopes) {
    final Jwt jwt = authentication.getToken();
    return convert(jwt.getTokenValue(), targetAudience, scopes);
  }

  /**
   * Converts a raw JWT token string into a domain token exchange request.
   *
   * @param tokenValue the raw JWT token string
   * @param targetAudience the target service audience
   * @param scopes the requested scopes
   * @return the domain token exchange request
   */
  public TokenExchangeRequest convert(
      final String tokenValue, final String targetAudience, final Set<String> scopes) {
    return TokenExchangeRequest.builder()
        .subjectToken(tokenValue)
        .subjectTokenType(TokenType.ACCESS_TOKEN)
        .grantType(GrantType.TOKEN_EXCHANGE)
        .audience(targetAudience)
        .scopes(scopes)
        .build();
  }
}
