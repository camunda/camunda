/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.auth.OidcPrincipalLoader;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

public class OAuth2TokenAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final OidcPrincipalLoader principalLoader;
  private final OidcGroupsLoader groupsLoader;
  private final String usernameClaim;
  private final String clientIdClaim;

  public OAuth2TokenAuthenticationConverter(
      final String usernameClaim, final String clientIdClaim, final String groupsClaim) {
    this.usernameClaim = usernameClaim;
    this.clientIdClaim = clientIdClaim;
    principalLoader = new OidcPrincipalLoader(usernameClaim, clientIdClaim);
    groupsLoader = new OidcGroupsLoader(groupsClaim);
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return authentication instanceof AbstractOAuth2TokenAuthenticationToken<? extends OAuth2Token>;
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    final var tokenAuthentication =
        (AbstractOAuth2TokenAuthenticationToken<? extends OAuth2Token>) authentication;
    final var attributes = tokenAuthentication.getTokenAttributes();

    final var principals = principalLoader.load(attributes);
    final var username = principals.username();
    final var clientId = principals.clientId();

    if (username == null && clientId == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT),
          "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, clientIdClaim));
    }

    final var groupIds = groupsLoader.load(attributes);
    return CamundaAuthentication.of(
        b -> b.username(username).clientId(clientId).claims(attributes).groupIds(groupIds));
  }
}
