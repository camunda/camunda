/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.auth.TokenIssuer;
import io.camunda.security.core.oidc.OidcGroupsExtractor;
import io.camunda.security.oidc.OidcClaimExtractor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a user's groups from the groups claim of the OIDC provider that issued their token. With
 * several providers, only some may have a groups claim; the users of the others get their groups
 * from Camunda.
 */
final class ProviderGroupsClaims {

  private final AuthenticationConfiguration authentication;
  // Keyed by the configured groups claims, so it holds one extractor per claim.
  private final Map<String, OidcGroupsExtractor> extractors = new ConcurrentHashMap<>();

  ProviderGroupsClaims(final AuthenticationConfiguration authentication) {
    this.authentication = authentication;
  }

  /**
   * @param tokenClaims the claims of the user's token
   * @param context names the caller when the claim can't be read
   * @return the groups in the claim, without duplicates, or empty when the token's provider has no
   *     groups claim, so the user's groups come from Camunda
   */
  Optional<List<String>> groupIds(final Map<String, Object> tokenClaims, final String context) {
    final var groupsClaim = authentication.getGroupsClaim(TokenIssuer.of(tokenClaims));
    if (groupsClaim == null) {
      return Optional.empty();
    }
    final var extractor = extractors.computeIfAbsent(groupsClaim, OidcGroupsExtractor::new);
    return Optional.of(
        OidcClaimExtractor.extractOrFallback(
                () -> extractor.extract(tokenClaims), List.<String>of(), context)
            .stream()
            .distinct()
            .toList());
  }
}
