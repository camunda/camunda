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
  private final boolean entraTokenVersionCheckEnabled;
  private final MembershipService membershipService;

  public TokenClaimsConverter(
      final SecurityConfiguration securityConfiguration,
      final MembershipService membershipService) {
    this.membershipService = membershipService;
    usernameClaim = securityConfiguration.getAuthentication().getOidc().getUsernameClaim();
    clientIdClaim = securityConfiguration.getAuthentication().getOidc().getClientIdClaim();
    preferUsernameClaim =
        securityConfiguration.getAuthentication().getOidc().isPreferUsernameClaim();
    entraTokenVersionCheckEnabled =
        securityConfiguration.getAuthentication().getOidc().isEntraTokenVersionCheckEnabled();
    oidcPrincipalLoader = new OidcPrincipalLoader(usernameClaim, clientIdClaim);
  }

  public CamundaAuthentication convert(final Map<String, Object> tokenClaims) {
    if (entraTokenVersionCheckEnabled) {
      validateEntraTokenVersion(tokenClaims);
    }

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

    final var resolver = membershipService.newResolver(tokenClaims, principalName, principalType);
    return CamundaAuthentication.of(
        a -> {
          if (principalType == PrincipalType.CLIENT) {
            a.clientId(principalName);
          } else {
            a.user(principalName);
          }
          return a.mappingRulesSupplier(resolver::mappingRules)
              .groupIdsSupplier(resolver::groups)
              .roleIdsSupplier(resolver::roles)
              .tenantsSupplier(resolver::tenants)
              .claims(tokenClaims);
        });
  }

  /**
   * Fails authentication hard when a Microsoft Entra (Azure AD) token is not a v2.0 token.
   *
   * <p>{@code convert()} runs on both access-token claims (API/bearer flow) and ID-token claims
   * (webapp login flow), so two distinct misconfigurations land here. Access tokens are emitted as
   * v1.0 when the app registration leaves {@code api.requestedAccessTokenVersion} unset; ID tokens
   * are v1.0 when the v1.0 authority is used instead of a {@code /v2.0} issuer endpoint. Both
   * surface as {@code iss=sts.windows.net}, {@code ver=1.0}, fail downstream validation, and
   * typically manifest as a silent redirect loop back to Entra. Detecting the mismatch here turns
   * that into a clear, actionable failure for operators.
   *
   * <p>Can be disabled via {@code
   * camunda.security.authentication.oidc.entraTokenVersionCheckEnabled} as a safety hatch, in case
   * this check incorrectly rejects a previously-working v1 authentication.
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

    // Logged at WARN, not ERROR: this is a client/configuration fault (a bad incoming token) that
    // is per-request and fully caller-controllable, so a client replaying a v1 token must not be
    // able to flood ERROR logs or trip alerting. No stacktrace is logged for the same reason.
    //
    // This log mainly serves the bearer/API flow, whose entry point
    // (AuthenticationEntryPointFailureHandler + BearerTokenAuthenticationEntryPoint) returns 401
    // silently, leaving the actionable guidance otherwise invisible. On the webapp/login chain it
    // is somewhat redundant with the container log of the bubbled OAuth2AuthenticationException;
    // the
    // guidance also lives in the exception message below.
    LOG.warn(
        "Rejected a Microsoft Entra token from issuer '{}' with an unsupported token version (ver='{}'). "
            + "Camunda requires v2.0 tokens. For access tokens (API/bearer flow), set 'api.requestedAccessTokenVersion' to 2 "
            + "in the Entra app registration manifest (Azure portal: App registrations > your app > Manifest). For ID tokens "
            + "(webapp login flow), configure the v2.0 authority by using an issuer-uri that ends in '/v2.0'. Then retry; "
            + "v1.0 tokens otherwise fail validation and cause a redirect loop back to Entra.",
        issuerUri,
        version);

    throw new OAuth2AuthenticationException(
        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN),
        "Microsoft Entra token version '%s' is not supported; v2.0 is required. For access tokens, set api.requestedAccessTokenVersion = 2 in the Entra app registration manifest; for ID tokens (login flow), use a v2.0 issuer-uri ending in /v2.0."
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
