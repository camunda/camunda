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
import java.util.Optional;
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
  private final String organizationId;

  public RoleValidator(final List<String> allowedRoles, final String organizationId) {
    this.allowedRoles = Objects.requireNonNull(allowedRoles, "allowedRoles must not be null");
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final var claimValue = token.getClaims().get(ORGANIZATION_CLAIM_KEY);

    if (!(claimValue instanceof Collection<?> claimedOrgs)) {
      LOG.debug(
          "Rejected token: missing or invalid organization claim '{}'", ORGANIZATION_CLAIM_KEY);
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "Token does not contain required organization claim for Optimize access.",
              null));
    }

    return getClaimedOrgById(claimedOrgs)
        .filter(this::hasAllowedRole)
        .map(org -> OAuth2TokenValidatorResult.success())
        .orElseGet(
            () -> {
              LOG.debug(
                  "Rejected token with organizations '{}', required roles: {}",
                  claimValue,
                  allowedRoles);
              return OAuth2TokenValidatorResult.failure(
                  new OAuth2Error(
                      OAuth2ErrorCodes.INVALID_TOKEN,
                      "Token does not contain required organization role for Optimize access. Required roles: %s"
                          .formatted(allowedRoles),
                      null));
            });
  }

  private Optional<? extends Map<?, ?>> getClaimedOrgById(final Collection<?> claimedOrgs) {
    LOG.debug("Getting claimed organization with id: {}", organizationId);
    return claimedOrgs.stream()
        .filter(org -> org instanceof Map<?, ?>)
        .map(org -> (Map<?, ?>) org)
        .filter(org -> organizationId.equals(org.get("id")))
        .findFirst();
  }

  private boolean hasAllowedRole(final Map<?, ?> claimedOrg) {
    final Object rolesObj = claimedOrg.get("roles");
    if (!(rolesObj instanceof Collection<?> userRoles)) {
      return false;
    }
    return userRoles.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .anyMatch(
            role -> {
              if (allowedRoles.contains(role)) {
                LOG.debug("User has allowed role '{}' for Optimize access", role);
                return true;
              }
              return false;
            });
  }
}
