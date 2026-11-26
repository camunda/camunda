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
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/**
 * The SaaS organization and cluster checks of a token. All SaaS clusters share one issuer and one
 * audience, so only these claims bind a token to its own cluster.
 */
@NullMarked
public final class SaasTokenValidators {

  private SaasTokenValidators() {}

  /**
   * @return the organization and cluster validators, or an empty list if SaaS is not configured
   * @throws IllegalStateException if only one of organization id and cluster id is configured
   */
  public static List<OAuth2TokenValidator<Jwt>> of(final SaasConfiguration saas) {
    if (!saas.isConfigured()) {
      return List.of();
    }
    return List.of(
        new OrganizationValidator(saas.getOrganizationId()),
        new ClusterValidator(saas.getClusterId()));
  }

  /**
   * Returns a decoder that also rejects a token of another SaaS organization or cluster with a
   * {@link JwtValidationException}. Returns the given decoder if SaaS is not configured.
   *
   * @throws IllegalStateException if only one of organization id and cluster id is configured
   */
  public static JwtDecoder withSaasValidation(
      final JwtDecoder decoder, final SaasConfiguration saas) {
    final var validators = of(saas);
    if (validators.isEmpty()) {
      return decoder;
    }
    final var validator = new DelegatingOAuth2TokenValidator<>(validators);
    return token -> {
      final Jwt jwt = decoder.decode(token);
      final var result = validator.validate(jwt);
      if (result.hasErrors()) {
        final var error = result.getErrors().iterator().next();
        throw new JwtValidationException(
            Objects.requireNonNullElse(error.getDescription(), error.getErrorCode()),
            result.getErrors());
      }
      return jwt;
    };
  }
}
