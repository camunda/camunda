/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.authentication.entity.AuthenticationContext.AuthenticationContextBuilder;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.authentication.service.MembershipService;
import io.camunda.security.auth.OidcPrincipalLoader;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class CamundaOAuthPrincipalServiceImpl implements CamundaOAuthPrincipalService {

  private final OidcPrincipalLoader oidcPrincipalLoader;
  private final String usernameClaim;
  private final String clientIdClaim;
  private final String groupsClaim;
  private final MembershipService membershipService;

  public CamundaOAuthPrincipalServiceImpl(
      final SecurityConfiguration securityConfiguration,
      final MembershipService membershipService) {
    this.membershipService = membershipService;
    usernameClaim = securityConfiguration.getAuthentication().getOidc().getUsernameClaim();
    clientIdClaim = securityConfiguration.getAuthentication().getOidc().getClientIdClaim();
    groupsClaim = securityConfiguration.getAuthentication().getOidc().getGroupsClaim();
    oidcPrincipalLoader = new OidcPrincipalLoader(usernameClaim, clientIdClaim);
  }

  @Override
  public OAuthContext loadOAuthContext(final Map<String, Object> claims)
      throws OAuth2AuthenticationException {
    final var principals = oidcPrincipalLoader.load(claims);
    final var username = principals.username();
    final var clientId = principals.clientId();

    if (username == null && clientId == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT),
          "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, clientIdClaim));
    }

    final var membershipResult = membershipService.resolveMemberships(claims, username, clientId);

    final var authContextBuilder = new AuthenticationContextBuilder();

    if (username != null) {
      authContextBuilder.withUsername(username);
    }

    if (clientId != null) {
      authContextBuilder.withClientId(clientId);
    }

    authContextBuilder
        .withTenants(membershipResult.tenants())
        .withGroups(membershipResult.groups().stream().toList())
        .withRoles(membershipResult.roles().stream().toList())
        .withGroupsClaimEnabled(StringUtils.hasText(groupsClaim));

    return new OAuthContext(membershipResult.mappings(), authContextBuilder.build());
  }
}
