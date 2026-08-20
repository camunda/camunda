/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

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

public class OrganizationValidator implements OAuth2TokenValidator<Jwt> {

  static final String ORGANIZATION_CLAIM_KEY = "https://camunda.com/orgs";
  private static final Logger LOG = LoggerFactory.getLogger(OrganizationValidator.class);
  private final String organizationId;

  public OrganizationValidator(final String organizationId) {
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId must not be null");
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final var claimValue = token.getClaims().get(ORGANIZATION_CLAIM_KEY);
    if (claimValue == null) {
      // Not all tokens contain an organization claim, only validate those that do.
      return OAuth2TokenValidatorResult.success();
    }

    if (claimValue instanceof final Collection<?> claimedOrgs) {
      for (final Object claimedOrg : claimedOrgs) {
        if (claimedOrg instanceof final Map<?, ?> orgDetails) {
          if (organizationId.equals(orgDetails.get("id"))) {
            return OAuth2TokenValidatorResult.success();
          }
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
