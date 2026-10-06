/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.identity.sdk.authentication.exception.JsonWebKeyException;
import io.camunda.identity.sdk.authentication.exception.TokenExpiredException;
import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * CCSM component access: the user must hold the Optimize permission in Management Identity. The
 * permission is resolved by the Identity SDK, which reads it from the token claim on Keycloak and
 * calls Management Identity on every other provider.
 *
 * <p>The token is taken from the current session rather than from the claims of the authentication,
 * because the Identity SDK needs the raw token to resolve the permission, and because the claims of
 * an authentication fall back to the id token when the access token cannot be decoded, which would
 * make the permission claim disappear instead of failing the check.
 */
@Component
@Conditional(CCSMCondition.class)
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OptimizeCcsmComponentAccessPolicy implements OptimizeComponentAccessPolicy {

  private static final Logger LOG =
      LoggerFactory.getLogger(OptimizeCcsmComponentAccessPolicy.class);

  private final CCSMTokenService tokenService;

  public OptimizeCcsmComponentAccessPolicy(final CCSMTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Override
  public Either<String, Void> checkAccess(final CamundaAuthentication authentication) {
    return checkAccessToken(tokenService.getCurrentUserAuthToken().orElse(null));
  }

  private Either<String, Void> checkAccessToken(final String accessToken) {
    if (accessToken == null) {
      LOG.debug("No access token available, skipping the Optimize permission check");
      return Either.right(null);
    }
    try {
      tokenService.verifyAccessToken(accessToken);
      return Either.right(null);
    } catch (final NotAuthorizedException e) {
      return Either.left(e.getMessage());
    } catch (final TokenExpiredException e) {
      // An expired token is renewed by the webapp chain and passed through by the API chain, so
      // treating it as a denial here would log out a user whose permission is intact. The cost is
      // that a revoked user keeps API access with the expired token, because the API chain does
      // not renew it, until the next web app request renews it and denies them.
      LOG.debug("Access token has expired: {}", e.getMessage());
      return Either.right(null);
    } catch (final JsonWebKeyException e) {
      // Denies like any other verification error, but logs a warning: when the keys cannot be
      // fetched, every user is denied, not only a user without the permission.
      LOG.warn("Could not fetch the keys to verify the access token, denying access", e);
      return Either.left("Access token could not be verified: " + e.getMessage());
    } catch (final TokenVerificationException e) {
      // Any verification error other than expiry is a denial. OptimizeBearerPermissionFilter makes
      // the same decision for bearer requests.
      return Either.left("Access token could not be verified: " + e.getMessage());
    }
  }
}
