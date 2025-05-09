/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class ClusterValidator implements OAuth2TokenValidator<Jwt> {
  static final String CLUSTER_CLAIM_KEY = "https://camunda.com/clusterId";
  private static final Logger LOG = LoggerFactory.getLogger(ClusterValidator.class);

  private final String clusterId;

  public ClusterValidator(final String clusterId) {
    this.clusterId = Objects.requireNonNull(clusterId, "clusterId must not be null");
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final var claimValue = token.getClaims().get(CLUSTER_CLAIM_KEY);

    if (claimValue == null) {
      // Not all tokens contain a cluster id claim, only validate those that do.
      return OAuth2TokenValidatorResult.success();
    }

    if (clusterId.equals(claimValue)) {
      return OAuth2TokenValidatorResult.success();
    }

    LOG.debug("Rejected token with cluster id '{}', expected {}", claimValue, clusterId);
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Token claims cluster id %s, expected %s".formatted(claimValue, clusterId),
            null));
  }
}
