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
    LOG.info("RoleValidator initialized with allowed roles: {}", allowedRoles);
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    LOG.info("Starting JWT role validation for token with subject: {}", token.getSubject());
    LOG.info("Token claims available: {}", token.getClaims().keySet());

    final var claimValue = token.getClaims().get(ORGANIZATION_CLAIM_KEY);
    LOG.info("Organization claim '{}' value: {}", ORGANIZATION_CLAIM_KEY, claimValue);

    if (claimValue == null) {
      LOG.info("Rejected token: missing organization claim '{}'", ORGANIZATION_CLAIM_KEY);
      LOG.debug("Rejected token: missing organization claim '{}'", ORGANIZATION_CLAIM_KEY);
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "Token does not contain required organization claim for Optimize access.",
              null));
    }

    if (claimValue instanceof final Collection<?> claimedOrgs) {
      LOG.info("Processing organization claim as collection with {} entries", claimedOrgs.size());
      LOG.info("Organization entries: {}", claimedOrgs);

      if (hasAllowedRole(claimedOrgs)) {
        LOG.info("JWT role validation successful - user has required role");
        return OAuth2TokenValidatorResult.success();
      }
    } else {
      LOG.info(
          "Organization claim is not a collection, actual type: {}",
          claimValue.getClass().getSimpleName());
    }

    LOG.info(
        "Rejected token with organizations '{}', required roles: {}", claimValue, allowedRoles);
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
    LOG.info("Checking for allowed roles in {} organizations", claimedOrgs.size());

    for (final Object claimedOrg : claimedOrgs) {
      LOG.info("Processing organization entry: {}", claimedOrg);

      if (claimedOrg instanceof final Map<?, ?> orgDetails) {
        LOG.info("Organization details map keys: {}", orgDetails.keySet());
        LOG.info("Organization details: {}", orgDetails);

        final Object rolesObj = orgDetails.get("roles");
        LOG.info("Roles object from organization: {}", rolesObj);

        if (rolesObj instanceof final Collection<?> userRoles) {
          LOG.info("Processing {} user roles: {}", userRoles.size(), userRoles);

          for (final Object userRole : userRoles) {
            LOG.info(
                "Checking user role: {} (type: {})",
                userRole,
                userRole != null ? userRole.getClass().getSimpleName() : "null");

            if (userRole instanceof String && allowedRoles.contains(userRole)) {
              LOG.info(
                  "User has allowed role '{}' for Optimize access - VALIDATION SUCCESSFUL",
                  userRole);
              LOG.debug("User has allowed role '{}' for Optimize access", userRole);
              return true;
            } else if (userRole instanceof String) {
              LOG.info("User role '{}' is not in allowed roles list: {}", userRole, allowedRoles);
            } else {
              LOG.info("User role is not a string: {}", userRole);
            }
          }
        } else {
          LOG.info(
              "Roles object is not a collection, actual type: {}",
              rolesObj != null ? rolesObj.getClass().getSimpleName() : "null");
        }
      } else {
        LOG.info(
            "Organization entry is not a Map, actual type: {}",
            claimedOrg != null ? claimedOrg.getClass().getSimpleName() : "null");
      }
    }

    LOG.info("No allowed roles found in any organization - VALIDATION FAILED");
    return false;
  }
}
