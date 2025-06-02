/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.config.OidcClientRegistration;
import io.camunda.authentication.entity.CamundaJwtUser;
import io.camunda.security.entity.AuthenticationMethod;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class CamundaJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
  final CamundaOAuthPrincipalService camundaOAuthPrincipalService;
  final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
  final OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

  public CamundaJwtAuthenticationConverter(
      final CamundaOAuthPrincipalService camundaOAuthPrincipalService,
      final OAuth2AuthorizedClientService oAuth2AuthorizedClientService,
      final OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager) {
    System.out.println("Converter created");
    this.camundaOAuthPrincipalService = camundaOAuthPrincipalService;
    this.oAuth2AuthorizedClientService = oAuth2AuthorizedClientService;
    this.oAuth2AuthorizedClientManager = oAuth2AuthorizedClientManager;
  }

  @Override
  public AbstractAuthenticationToken convert(final Jwt source) {
    System.out.println("IPETROV: auth converter executed");
    final AbstractAuthenticationToken token = jwtAuthenticationConverter.convert(source);
    final OAuth2AuthorizedClient authorizedClient =
        oAuth2AuthorizedClientService.loadAuthorizedClient(
            OidcClientRegistration.REGISTRATION_ID,
            SecurityContextHolder.getContext().getAuthentication().getName());
    return new CamundaJwtAuthenticationToken(
        source,
        new CamundaJwtUser(
            source, camundaOAuthPrincipalService.loadOAuthContext(source.getClaims())),
        authorizedClient,
        token.getCredentials(),
        token.getAuthorities(),
        oAuth2AuthorizedClientManager::authorize);
  }
}
