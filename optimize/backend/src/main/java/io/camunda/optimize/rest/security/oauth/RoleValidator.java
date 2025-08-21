/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.oauth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class RoleValidator implements OAuth2TokenValidator<Jwt> {

  static final String ORGANIZATION_CLAIM_KEY = "https://camunda.com/orgs";
  private static final Logger LOG = LoggerFactory.getLogger(RoleValidator.class);

  private final List<String> allowedRoles;

  public RoleValidator(final List<String> allowedRoles) {
    this.allowedRoles = Objects.requireNonNull(allowedRoles, "allowedRoles must not be null");
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final var claimValue = token.getClaims().get(ORGANIZATION_CLAIM_KEY);
    if (claimValue == null) {
      LOG.debug("Rejected token: missing organization claim '{}'", ORGANIZATION_CLAIM_KEY);
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "Token does not contain required organization claim for Optimize access.",
              null));
    }

    if (claimValue instanceof final Collection<?> claimedOrgs) {
      if (hasAllowedRole(claimedOrgs)) {
        return OAuth2TokenValidatorResult.success();
      }
    }

    LOG.debug(
        "Rejected token with organizations '{}', required roles: {}", claimValue, allowedRoles);
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Token does not contain required organization role for Optimize access. Required roles: %s"
                .formatted(allowedRoles),
            null));
  }

  private boolean hasAllowedRole(final Collection<?> claimedOrgs) {
    for (final Object claimedOrg : claimedOrgs) {
      if (claimedOrg instanceof final Map<?, ?> orgDetails) {
        final Object rolesObj = orgDetails.get("roles");
        if (rolesObj instanceof final Collection<?> userRoles) {
          for (final Object userRole : userRoles) {
            if (userRole instanceof String && allowedRoles.contains(userRole)) {
              LOG.debug("User has allowed role '{}' for Optimize access", userRole);
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}
