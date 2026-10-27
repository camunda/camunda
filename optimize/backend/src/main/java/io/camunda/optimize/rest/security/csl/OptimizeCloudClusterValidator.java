/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * CCSaaS cluster gate, mirroring OC's {@code io.camunda.authentication.config.ClusterValidator}.
 * Applied to every token that carries the {@code https://camunda.com/clusterId} claim: the claim
 * must equal the configured cluster id.
 *
 * <p><em>Lenient on absence</em>, matching OC and the CSL adoption baseline: a token without the
 * cluster id claim (for example the interactive login id_token) passes this validator, so the
 * single shared {@link io.camunda.security.spring.oidc.TokenValidatorFactory} can serve both the
 * login id_token and machine-to-machine bearer tokens.
 */
public final class OptimizeCloudClusterValidator implements OAuth2TokenValidator<Jwt> {

  /** Auth0 claim carrying the cluster id a token is scoped to. */
  static final String CLUSTER_CLAIM = "https://camunda.com/clusterId";

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeCloudClusterValidator.class);

  private final String clusterId;

  public OptimizeCloudClusterValidator(final String clusterId) {
    this.clusterId = Objects.requireNonNull(clusterId, "clusterId must not be null");
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final Object claimValue = token.getClaims().get(CLUSTER_CLAIM);
    if (claimValue == null) {
      // Not all tokens carry a cluster id claim; only validate those that do.
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
