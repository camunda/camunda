/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.identity.sdk.exception.IdentityException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.CCSMTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * CCSM Identity {@code write:*} (OPTIMIZE_PERMISSION) gate for access tokens. Delegates to {@link
 * CCSMTokenService#verifyAccessToken(String)}, because CSL's own JWT validation only checks issuer,
 * signature and expiry and has no concept of Identity's per-application permission grant.
 *
 * <p>The delegation is a full identity-sdk re-verification and includes a hard match of the token's
 * {@code aud} claim against {@code camunda.identity.audience}, unlike {@link
 * OptimizeCloudOrganizationValidator}/{@code OptimizeCloudClusterValidator} which are lenient on an
 * audience mismatch. Only use it on paths that carry a genuine access token, never on the login
 * id_token, which is audienced to the OIDC client-id instead and would always be rejected.
 */
public final class OptimizeIdentityPermissionValidator implements OAuth2TokenValidator<Jwt> {

  private static final Logger LOG =
      LoggerFactory.getLogger(OptimizeIdentityPermissionValidator.class);

  private final CCSMTokenService ccsmTokenService;

  public OptimizeIdentityPermissionValidator(final CCSMTokenService ccsmTokenService) {
    this.ccsmTokenService = ccsmTokenService;
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    try {
      ccsmTokenService.verifyAccessToken(token.getTokenValue());
      return OAuth2TokenValidatorResult.success();
    } catch (final NotAuthorizedException e) {
      // NotAuthorizedException also covers CCSMTokenService's Entra v1.0 token-version rejection,
      // not only a genuine missing write:* grant, and the two cannot be distinguished here — so the
      // message must not claim a specific cause it cannot verify.
      LOG.debug("Rejected token: not authorized to access Optimize", e);
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              OAuth2ErrorCodes.INSUFFICIENT_SCOPE,
              "Token could not be verified as authorized",
              null));
    } catch (final IdentityException e) {
      // Covers TokenVerificationException (invalid/expired token) and RestException (Identity
      // unreachable) alike: either way the token cannot be confirmed to carry the permission.
      LOG.debug("Rejected token: Identity could not verify it", e);
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "Token could not be verified against Identity",
              null));
    } catch (final RuntimeException e) {
      // A security boundary must fail closed on the unexpected, not propagate it: an uncaught
      // exception here would surface as an unhandled 500 instead of a clean rejection, and could
      // let a token through some chains treat validator errors more leniently than others.
      LOG.warn("Rejected token: unexpected error verifying it against Identity", e);
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "Token could not be verified against Identity",
              null));
    }
  }
}
