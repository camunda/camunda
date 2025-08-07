/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;

import io.camunda.authentication.service.MembershipService;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.OidcPrincipalLoader;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

public class TokenClaimsConverter {

  private final OidcPrincipalLoader oidcPrincipalLoader;
  private final String usernameClaim;
  private final String clientIdClaim;
  private final MembershipService membershipService;

  public TokenClaimsConverter(
      final SecurityConfiguration securityConfiguration,
      final MembershipService membershipService) {
    this.membershipService = membershipService;
    usernameClaim = securityConfiguration.getAuthentication().getOidc().getUsernameClaim();
    clientIdClaim = securityConfiguration.getAuthentication().getOidc().getClientIdClaim();
    oidcPrincipalLoader = new OidcPrincipalLoader(usernameClaim, clientIdClaim);
  }

  public CamundaAuthentication convert(final Map<String, Object> tokenClaims) {
    final var principals = oidcPrincipalLoader.load(tokenClaims);
    final var username = principals.username();
    final var clientId = principals.clientId();

    // will be used when sending a broker request
    final var authenticatedClaims = new HashMap<String, Object>();
    authenticatedClaims.put(USER_TOKEN_CLAIMS, tokenClaims);

    if (username == null && clientId == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT),
          "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, clientIdClaim));
    }
    if (username != null) {
      authenticatedClaims.put(AUTHORIZED_USERNAME, username);
    }

    if (clientId != null) {
      authenticatedClaims.put(AUTHORIZED_CLIENT_ID, clientId);
    }

    return membershipService.resolveMemberships(
        tokenClaims, authenticatedClaims, username, clientId);
  }
}
