/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.resolver;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class for extracting authorization-related information from claims.
 *
 * <p>This class provides methods to extract user information, client information, group
 * memberships, and other claims from authorization data. It is used by the authorization check
 * behavior to access claims data in a consistent way.
 */
public final class ClaimsExtractor {

  private final MembershipState membershipState;

  public ClaimsExtractor(final MembershipState membershipState) {
    this.membershipState = membershipState;
  }

  /**
   * Extracts the username from an authorization request.
   *
   * @param request the authorization request
   * @return an Optional containing the username if present
   */
  public Optional<String> getUsername(final AuthorizationRequest request) {
    return getUsername(request.claims());
  }

  /**
   * Extracts the username from authorization claims.
   *
   * @param authorizationClaims the authorization claims map
   * @return an Optional containing the username if present
   */
  public Optional<String> getUsername(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable((String) authorizationClaims.get(Authorization.AUTHORIZED_USERNAME));
  }

  /**
   * Extracts the client ID from an authorization request.
   *
   * @param request the authorization request
   * @return an Optional containing the client ID if present
   */
  public Optional<String> getClientId(final AuthorizationRequest request) {
    return getClientId(request.claims());
  }

  /**
   * Extracts the client ID from authorization claims.
   *
   * @param authorizationClaims the authorization claims map
   * @return an Optional containing the client ID if present
   */
  public Optional<String> getClientId(final Map<String, Object> authorizationClaims) {
    return Optional.ofNullable(
        (String) authorizationClaims.get(Authorization.AUTHORIZED_CLIENT_ID));
  }

  /**
   * Checks if a command is executed by an anonymous authentication.
   *
   * @param authorizationClaims the authorization claims map
   * @return {@code true} if the user is authorized as anonymous, {@code false} otherwise
   */
  public boolean isAuthorizedAnonymousUser(final Map<String, Object> authorizationClaims) {
    final var authorizedAnonymousUserClaim =
        authorizationClaims.get(Authorization.AUTHORIZED_ANONYMOUS_USER);
    return Optional.ofNullable(authorizedAnonymousUserClaim).map(Boolean.class::cast).orElse(false);
  }

  /**
   * Retrieves the groups for a given entity. First checks if groups are provided in the claims,
   * otherwise falls back to querying the membership state.
   *
   * @param authorizations the authorization claims map
   * @param ownerType the type of the entity
   * @param ownerId the ID of the entity
   * @return a list of group IDs
   */
  public List<String> getGroups(
      final Map<String, Object> authorizations, final EntityType ownerType, final String ownerId) {
    final List<String> groupsClaims =
        (List<String>) authorizations.get(Authorization.USER_GROUPS_CLAIMS);
    if (groupsClaims != null) {
      return groupsClaims;
    }
    return membershipState.getMemberships(ownerType, ownerId, RelationType.GROUP);
  }

  /**
   * Extracts the token claims from authorization claims.
   *
   * @param claims the authorization claims map
   * @return a map containing the token claims, or an empty map if not present
   */
  public Map<String, Object> getTokenClaims(final Map<String, Object> claims) {
    return (Map<String, Object>) claims.getOrDefault(Authorization.USER_TOKEN_CLAIMS, Map.of());
  }
}
