/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.authentication.entity.AuthenticationContext.AuthenticationContextBuilder;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

public class PrincipalExtractionHelper {

  public static PrincipalExtractionResult extractPrincipals(
      final io.camunda.security.auth.OidcPrincipalLoader oidcPrincipalLoader,
      final Map<String, Object> claims,
      final String usernameClaim,
      final String clientIdClaim)
      throws OAuth2AuthenticationException {
    final var authContextBuilder = new AuthenticationContextBuilder();
    final var principals = oidcPrincipalLoader.load(claims);
    final var username = principals.username();
    final var clientId = principals.clientId();
    final var ownerTypeToIds = new HashMap<EntityType, Set<String>>();

    if (username == null && clientId == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT),
          "Neither username claim (%s) nor clientId claim (%s) could be found in the claims. Please check your OIDC configuration."
              .formatted(usernameClaim, clientIdClaim));
    }
    if (username != null) {
      authContextBuilder.withUsername(username);
      ownerTypeToIds.put(EntityType.USER, Set.of(username));
    }
    if (clientId != null) {
      authContextBuilder.withClientId(clientId);
      ownerTypeToIds.put(EntityType.CLIENT, Set.of(clientId));
    }
    return new PrincipalExtractionResult(authContextBuilder, ownerTypeToIds, username, clientId);
  }

  public static class PrincipalExtractionResult {
    public final AuthenticationContextBuilder authContextBuilder;
    public final Map<EntityType, Set<String>> ownerTypeToIds;
    public final String username;
    public final String clientId;

    public PrincipalExtractionResult(
        final AuthenticationContextBuilder builder,
        final Map<EntityType, Set<String>> ownerTypeToIds,
        final String username,
        final String clientId) {
      authContextBuilder = builder;
      this.ownerTypeToIds = ownerTypeToIds;
      this.username = username;
      this.clientId = clientId;
    }
  }
}
