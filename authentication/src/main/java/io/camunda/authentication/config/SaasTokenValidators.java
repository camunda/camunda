/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.api.model.config.SaasConfiguration;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Gives the SaaS organization and cluster checks of a token. All SaaS clusters share one issuer and
 * one audience, so only these claims bind a token to its own cluster.
 */
@NullMarked
public final class SaasTokenValidators {

  private SaasTokenValidators() {}

  /**
   * @return the organization and cluster validators, or an empty list without SaaS configuration
   * @throws IllegalStateException if only one of organization id and cluster id has a value
   */
  public static List<OAuth2TokenValidator<Jwt>> of(final SaasConfiguration saas) {
    if (!saas.isConfigured()) {
      return List.of();
    }
    return List.of(
        new OrganizationValidator(saas.getOrganizationId()),
        new ClusterValidator(saas.getClusterId()));
  }
}
