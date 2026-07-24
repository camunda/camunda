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
 * CCSaaS cluster binding as an {@link OAuth2TokenValidator}. Mirrors OC's {@code ClusterValidator}:
 * the {@code https://camunda.com/clusterId} claim, when present, must equal the configured cluster
 * id. Composed into CSL's {@code TokenValidatorFactory} so it applies to both the bearer access
 * token and the interactive login id_token. See {@link OptimizeCloudSecurityConfiguration}.
 *
 * <p><em>Lenient on absence</em>: a token without the cluster id claim passes, matching OC — the
 * interactive login id_token does not always carry it.
 */
public final class OptimizeCloudClusterValidator implements OAuth2TokenValidator<Jwt> {

  /** Auth0 claim carrying the cluster the token was issued for. */
  static final String CLUSTER_ID_CLAIM = "https://camunda.com/clusterId";

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeCloudClusterValidator.class);

  private final String clusterId;

  public OptimizeCloudClusterValidator(final String clusterId) {
    this.clusterId = Objects.requireNonNull(clusterId, "clusterId must not be null");
  }

  @Override
  public OAuth2TokenValidatorResult validate(final Jwt token) {
    final Object claim = token.getClaims().get(CLUSTER_ID_CLAIM);
    if (claim == null) {
      // Not all tokens carry a cluster id claim; only validate those that do.
      return OAuth2TokenValidatorResult.success();
    }
    if (clusterId.equals(claim)) {
      return OAuth2TokenValidatorResult.success();
    }
    LOG.debug("Rejected token: cluster id claim does not match cluster [{}]", clusterId);
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Token cluster id does not match %s".formatted(clusterId),
            null));
  }
}
