/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class CamundaOidcUserService extends OidcUserService {
  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaOidcUserService.class);
  private final CamundaOAuthPrincipalService camundaOAuthPrincipalService;
  private final JwtDecoder jwtDecoder;

  public CamundaOidcUserService(
      final CamundaOAuthPrincipalService camundaOAuthPrincipalService,
      final JwtDecoder jwtDecoder) {
    this.camundaOAuthPrincipalService = camundaOAuthPrincipalService;
    this.jwtDecoder = jwtDecoder;
  }

  @Override
  public OidcUser loadUser(final OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    final OidcUser oidcUser = super.loadUser(userRequest);
    Map<String, Object> claims;
    try {
      final var jwt = jwtDecoder.decode(userRequest.getAccessToken().getTokenValue());
      claims = jwt.getClaims();
      return new CamundaOidcUser(
          oidcUser, jwt.getTokenValue(), camundaOAuthPrincipalService.loadOAuthContext(claims));
    } catch (final JwtException e) {
      LOGGER.warn(
          "Failed to decode access token: {}, falling back to ID Token claims", e.getMessage());
      claims = oidcUser.getIdToken().getClaims();
      return new CamundaOidcUser(
          oidcUser, null, camundaOAuthPrincipalService.loadOAuthContext(claims));
    }
  }
}
