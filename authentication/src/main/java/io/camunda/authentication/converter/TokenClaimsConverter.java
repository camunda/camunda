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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

public class TokenClaimsConverter {

  private static final Logger LOG = LoggerFactory.getLogger(TokenClaimsConverter.class);

  // Microsoft identity platform issuer hosts. v2.0 tokens are issued by login.microsoftonline.com
  // (issuer ends in /v2.0); v1.0 tokens are issued by sts.windows.net.
  private static final String MS_ISSUER_V2_HOST = "login.microsoftonline.com";
  private static final String MS_ISSUER_V1_HOST = "sts.windows.net";
  private static final String ISSUER_CLAIM = "iss";
  private static final String TOKEN_VERSION_CLAIM = "ver";
  private static final String REQUIRED_ENTRA_TOKEN_VERSION = "2.0";

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

  public CamundaAuthentication convert(final Map<String, Object> tokenClaims) {
    validateEntraTokenVersion(tokenClaims);

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
   * Fails authentication hard when a Microsoft Entra (Azure AD) access token is not a v2.0 token.
   *
   * <p>Entra app registrations that leave {@code api.requestedAccessTokenVersion} unset emit v1.0
   * access tokens (issuer {@code sts.windows.net}, {@code ver=1.0}). These fail downstream
   * validation and typically manifest as a silent redirect loop back to Entra. Detecting the
   * mismatch here turns that into a clear, actionable failure for operators.
   */
  private void validateEntraTokenVersion(final Map<String, Object> tokenClaims) {
    final Object issuer = tokenClaims.get(ISSUER_CLAIM);
    if (!(issuer instanceof final String issuerUri) || !isMicrosoftIssuer(issuerUri)) {
      return;
    }

    final Object version = tokenClaims.get(TOKEN_VERSION_CLAIM);
    if (REQUIRED_ENTRA_TOKEN_VERSION.equals(version)) {
      return;
    }

    LOG.error(
        "Rejected a Microsoft Entra access token from issuer '{}' with an unsupported access token version (ver='{}'). "
            + "Camunda requires v2.0 access tokens. Set 'api.requestedAccessTokenVersion' to 2 in the Entra app registration "
            + "manifest (Azure portal: App registrations > your app > Manifest) so the identity platform issues v2.0 tokens, "
            + "then retry. v1.0 tokens otherwise fail validation and cause a redirect loop back to Entra.",
        issuerUri,
        version);

    throw new OAuth2AuthenticationException(
        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN),
        "Microsoft Entra access token version '%s' is not supported; v2.0 is required. Set api.requestedAccessTokenVersion = 2 in the Entra app registration manifest."
            .formatted(version));
  }

  private static boolean isMicrosoftIssuer(final String issuerUri) {
    try {
      final String host = java.net.URI.create(issuerUri).getHost();
      return MS_ISSUER_V2_HOST.equalsIgnoreCase(host) || MS_ISSUER_V1_HOST.equalsIgnoreCase(host);
    } catch (final RuntimeException e) {
      return false;
    }
  }
}
