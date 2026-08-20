/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.EntityType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the set of authorization-owner identities reachable by a given {@link
 * CamundaAuthentication}: the principal itself (user or client) plus every group, role, and mapping
 * rule it is a member of.
 *
 * <p>This mirrors the owner-id resolution used by {@code
 * io.camunda.security.core.authz.AuthorizationChecker} and is shared by every caller that needs to
 * query the authorization store for everything a principal — directly or transitively — has been
 * granted, e.g. {@code AuthorizationRepositoryAdapter} (host-supplied CSL port) and {@code
 * AuthorizationServices#searchOwnAuthorizations} (the {@code
 * /v2/authentication/me/authorizations/search} endpoint).
 */
public final class AuthenticatedOwnerIdsUtil {

  private AuthenticatedOwnerIdsUtil() {}

  /**
   * @return a map from {@link EntityType} to the set of owner ids of that type reachable by {@code
   *     authentication}. Entity types with no reachable owner ids are omitted. Returns an empty map
   *     for an anonymous principal with no memberships.
   */
  public static Map<EntityType, Set<String>> collect(final CamundaAuthentication authentication) {
    final var ownerTypeToOwnerIds = new HashMap<EntityType, Set<String>>();
    if (authentication.authenticatedUsername() != null) {
      ownerTypeToOwnerIds.put(EntityType.USER, Set.of(authentication.authenticatedUsername()));
    }
    if (authentication.authenticatedClientId() != null) {
      ownerTypeToOwnerIds.put(EntityType.CLIENT, Set.of(authentication.authenticatedClientId()));
    }
    final var groupIds = authentication.authenticatedGroupIds();
    if (groupIds != null && !groupIds.isEmpty()) {
      ownerTypeToOwnerIds.put(EntityType.GROUP, new HashSet<>(groupIds));
    }
    final var roleIds = authentication.authenticatedRoleIds();
    if (roleIds != null && !roleIds.isEmpty()) {
      ownerTypeToOwnerIds.put(EntityType.ROLE, new HashSet<>(roleIds));
    }
    final var mappingRuleIds = authentication.authenticatedMappingRuleIds();
    if (mappingRuleIds != null && !mappingRuleIds.isEmpty()) {
      ownerTypeToOwnerIds.put(EntityType.MAPPING_RULE, new HashSet<>(mappingRuleIds));
    }
    return ownerTypeToOwnerIds;
  }
}
