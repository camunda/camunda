/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.auth.TokenIssuer;
import io.camunda.security.configuration.AuthenticationConfiguration;
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
  // Keyed by the configured groups claims, so it holds one loader per claim.
  private final Map<String, OidcGroupsLoader> loaders = new ConcurrentHashMap<>();

  ProviderGroupsClaims(final AuthenticationConfiguration authentication) {
    this.authentication = authentication;
  }

  /**
   * @param tokenClaims the claims of the user's token
   * @return the groups in the claim, or empty when the token's provider has no groups claim, so the
   *     user's groups come from Camunda
   */
  Optional<List<String>> groupIds(final Map<String, Object> tokenClaims) {
    final var oidc = authentication.oidcForIssuer(TokenIssuer.of(tokenClaims));
    if (!oidc.isGroupsClaimConfigured()) {
      return Optional.empty();
    }
    final var loader = loaders.computeIfAbsent(oidc.getGroupsClaim(), OidcGroupsLoader::new);
    return Optional.of(List.copyOf(loader.load(tokenClaims)));
  }
}
