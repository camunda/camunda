/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import io.camunda.authentication.service.MembershipService;
import io.camunda.authentication.service.MembershipService.PrincipalType;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.OidcPrincipalLoader;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

public class TokenClaimsConverter {

  private final OidcPrincipalLoader oidcPrincipalLoader;
  private final String usernameClaim;
  private final String clientIdClaim;
  private final boolean preferUsernameClaim;
  private final MembershipService membershipService;

  public TokenClaimsConverter(
      final SecurityConfiguration securityConfiguration,
      final MembershipService membershipService) {
    this.membershipService = membershipService;
    usernameClaim = securityConfiguration.getAuthentication().getOidc().getUsernameClaim();
    clientIdClaim = securityConfiguration.getAuthentication().getOidc().getClientIdClaim();
    preferUsernameClaim =
        securityConfiguration.getAuthentication().getOidc().isPreferUsernameClaim();
    oidcPrincipalLoader = new OidcPrincipalLoader(usernameClaim, clientIdClaim);
  }

  /**
   * Converts token claims to a {@link CamundaAuthentication} with memberships resolved eagerly.
   * Use this for session-based authentication (browser OIDC flows) where the full auth must be
   * available before the HTTP session is persisted.
   */
  public CamundaAuthentication convert(final Map<String, Object> tokenClaims) {
    final var principals = oidcPrincipalLoader.load(tokenClaims);
    final var username = principals.username();
    final var clientId = principals.clientId();

    if (username == null && clientId == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT),
          "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, clientIdClaim));
    }

    final String principalName;
    final PrincipalType principalType;

    if ((preferUsernameClaim && username != null) || clientId == null) {
      principalName = username;
      principalType = PrincipalType.USER;
    } else {
      principalName = clientId;
      principalType = PrincipalType.CLIENT;
    }

    return membershipService.resolveMemberships(tokenClaims, principalName, principalType);
  }

  /**
   * Converts token claims to a {@link CamundaAuthentication} with memberships deferred until first
   * access. Use this for M2M bearer-token authentication where the same token is reused across many
   * requests and membership data (groups, roles, tenants, mapping rules) may never be needed (e.g.
   * on broker-only paths with authorization disabled).
   *
   * <p>The four secondary-storage queries are triggered on the first call to any of
   * {@code authenticatedGroupIds()}, {@code authenticatedRoleIds()},
   * {@code authenticatedTenantIds()}, or {@code authenticatedMappingRuleIds()}.
   */
  public CamundaAuthentication convertLazy(final Map<String, Object> tokenClaims) {
    final var principals = oidcPrincipalLoader.load(tokenClaims);
    final var username = principals.username();
    final var clientId = principals.clientId();

    if (username == null && clientId == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT),
          "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, clientIdClaim));
    }

    final String principalName;
    final PrincipalType principalType;

    if ((preferUsernameClaim && username != null) || clientId == null) {
      principalName = username;
      principalType = PrincipalType.USER;
    } else {
      principalName = clientId;
      principalType = PrincipalType.CLIENT;
    }

    return CamundaAuthentication.of(
        b -> {
          if (principalType == PrincipalType.CLIENT) {
            b.clientId(principalName);
          } else {
            b.user(principalName);
          }
          return b.claims(tokenClaims)
              .lazyMemberships(
                  () -> {
                    final CamundaAuthentication full =
                        membershipService.resolveMemberships(
                            tokenClaims, principalName, principalType);
                    return new CamundaAuthentication.MembershipData(
                        full.authenticatedGroupIds(),
                        full.authenticatedRoleIds(),
                        full.authenticatedTenantIds(),
                        full.authenticatedMappingRuleIds());
                  });
        });
  }
}
