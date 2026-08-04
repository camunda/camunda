/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

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

/**
 * CCSaaS organization + role gate. When a token carries the {@code https://camunda.com/orgs} claim,
 * the claim must list the configured organization with at least one allowed role ({@code
 * admin}/{@code analyst}/{@code owner}/{@code supportagent}), unlike OC's membership-only check.
 *
 * <p><em>Lenient on absence</em>, matching OC's shared-factory model: a token without the
 * organizations claim (for example a machine-to-machine bearer token) passes this validator, so the
 * single shared {@link io.camunda.security.spring.oidc.TokenValidatorFactory} can serve both the
 * interactive login id_token (carries orgs, not cluster id) and bearer tokens (carry cluster id,
 * not orgs).
 */
public final class OptimizeCloudOrganizationValidator implements OAuth2TokenValidator<Jwt> {

  /** Auth0 claim carrying the user's organizations, each a map of {@code id} + {@code roles}. */
  static final String ORGANIZATIONS_CLAIM = "https://camunda.com/orgs";

  /** Organization roles that grant Optimize access (mirrors the legacy adapter). */
  static final List<String> ALLOWED_ORG_ROLES =
      List.of("admin", "analyst", "owner", "supportagent");

  private static final Logger LOG =
      LoggerFactory.getLogger(OptimizeCloudOrganizationValidator.class);

  private final String organizationId;
  private final List<String> allowedRoles;

  public OptimizeCloudOrganizationValidator(
      final String organizationId, final List<String> allowedRoles) {
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
    this.allowedRoles = List.copyOf(allowedRoles);
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final Object claim = token.getClaims().get(ORGANIZATIONS_CLAIM);
    if (claim == null) {
      // Not all tokens carry an organizations claim; only validate those that do.
      return OAuth2TokenValidatorResult.success();
    }

    if (claim instanceof final Collection<?> organizations && grantsAllowedRole(organizations)) {
      return OAuth2TokenValidatorResult.success();
    }

    LOG.debug(
        "Rejected token: organizations claim does not grant organization [{}] an allowed role",
        organizationId);
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Token does not grant organization %s a required role %s"
                .formatted(organizationId, allowedRoles),
            null));
  }

  private boolean grantsAllowedRole(final Collection<?> organizations) {
    return organizations.stream()
        .filter(Map.class::isInstance)
        .map(org -> (Map<?, ?>) org)
        .filter(org -> organizationId.equals(org.get("id")))
        .anyMatch(
            org ->
                org.get("roles") instanceof final Collection<?> roles
                    && roles.stream().anyMatch(allowedRoles::contains));
  }
}
