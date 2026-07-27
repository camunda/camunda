/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import java.util.Collection;
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
 * CCSaaS organization membership gate, mirroring OC's {@code
 * io.camunda.authentication.config.OrganizationValidator}. Applied to every token that carries the
 * {@code https://camunda.com/orgs} claim: the claim must list the configured organization.
 *
 * <p><em>Lenient on absence</em>, matching OC and the CSL adoption baseline: a token without the
 * organizations claim (for example a machine-to-machine bearer token) passes this validator, so the
 * single shared {@link io.camunda.security.spring.oidc.TokenValidatorFactory} can serve both the
 * interactive login id_token (carries orgs, not cluster id) and bearer tokens (carry cluster id,
 * not orgs). This is an intentional behaviour change from Optimize 8.9, which denied login on a
 * missing/malformed orgs claim and additionally required an allowed org role. Fine-grained access
 * is now decided by CSL's authorization policy, not by the Auth0 org role.
 */
public final class OptimizeCloudOrganizationValidator implements OAuth2TokenValidator<Jwt> {

  /** Auth0 claim carrying the user's organizations, each a map of {@code id} + {@code roles}. */
  static final String ORGANIZATIONS_CLAIM = "https://camunda.com/orgs";

  private static final Logger LOG =
      LoggerFactory.getLogger(OptimizeCloudOrganizationValidator.class);

  private final String organizationId;

  public OptimizeCloudOrganizationValidator(final String organizationId) {
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final Object claimValue = token.getClaims().get(ORGANIZATIONS_CLAIM);
    if (claimValue == null) {
      // Not all tokens carry an organizations claim; only validate those that do.
      return OAuth2TokenValidatorResult.success();
    }

    if (claimValue instanceof final Collection<?> claimedOrgs) {
      for (final Object claimedOrg : claimedOrgs) {
        if (claimedOrg instanceof final Map<?, ?> orgDetails
            && organizationId.equals(orgDetails.get("id"))) {
          return OAuth2TokenValidatorResult.success();
        }
      }
    }

    LOG.debug("Rejected token with organizations '{}', expected {}", claimValue, organizationId);
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Token claims organizations %s, expected %s".formatted(claimValue, organizationId),
            null));
  }
}
