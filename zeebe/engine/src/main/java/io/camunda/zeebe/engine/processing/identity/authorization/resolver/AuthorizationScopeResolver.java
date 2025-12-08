/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.resolver;

import io.camunda.security.auth.MappingRuleMatcher;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Resolves authorization scopes for entities. */
public final class AuthorizationScopeResolver {

  private final AuthorizationState authorizationState;
  private final MembershipState membershipState;
  private final MappingRuleState mappingRuleState;
  private final ClaimsExtractor claimsExtractor;
  private final boolean authorizationsEnabled;

  public AuthorizationScopeResolver(
      final AuthorizationState authorizationState,
      final MembershipState membershipState,
      final MappingRuleState mappingRuleState,
      final ClaimsExtractor claimsExtractor,
      final boolean authorizationsEnabled) {
    this.authorizationState = authorizationState;
    this.membershipState = membershipState;
    this.mappingRuleState = mappingRuleState;
    this.claimsExtractor = claimsExtractor;
    this.authorizationsEnabled = authorizationsEnabled;
  }

  /**
   * Get all authorized authorization scopes for a given authorization request.
   *
   * <p>This method aggregates scopes from the authenticated user/client and any matching mapping
   * rules. If authorizations are disabled or the user is anonymous, returns wildcard scope.
   *
   * @param request the authorization request containing claims, resource type, and permission type
   * @return a set of authorized scopes for the request
   */
  public Set<AuthorizationScope> getAllAuthorizedScopes(final AuthorizationRequest request) {
    if (!authorizationsEnabled || claimsExtractor.isAuthorizedAnonymousUser(request.claims())) {
      return Set.of(AuthorizationScope.WILDCARD);
    }

    final var authorizedScopes = new HashSet<AuthorizationScope>();

    claimsExtractor
        .getClientId(request)
        .ifPresentOrElse(
            clientId ->
                getScopesForEntity(
                        request.claims(),
                        EntityType.CLIENT,
                        clientId,
                        request.resourceType(),
                        request.permissionType())
                    .forEach(authorizedScopes::add),
            // If a clientId was present, don't use the username
            () ->
                claimsExtractor
                    .getUsername(request)
                    .map(
                        username ->
                            getScopesForEntity(
                                request.claims(),
                                EntityType.USER,
                                username,
                                request.resourceType(),
                                request.permissionType()))
                    .ifPresent(idsForUsername -> idsForUsername.forEach(authorizedScopes::add)));

    // mapping rules can layer on top of username/client id
    getPersistedMappingRules(request)
        .flatMap(
            mappingRule ->
                getScopesForEntity(
                    request.claims(),
                    EntityType.MAPPING_RULE,
                    mappingRule.getMappingRuleId(),
                    request.resourceType(),
                    request.permissionType()))
        .forEach(authorizedScopes::add);

    return authorizedScopes;
  }

  /**
   * Get direct authorized authorization scopes for a given owner, resource type and permission
   * type.
   *
   * <p>This does not include inherited authorizations, for example authorizations for users from
   * assigned roles or groups.
   *
   * @param ownerType the type of the authorization owner (user, client, role, group, etc.)
   * @param ownerId the ID of the owner
   * @param resourceType the resource type to check permissions for
   * @param permissionType the permission type to check
   * @return a set of directly assigned authorization scopes
   */
  public Set<AuthorizationScope> getDirectScopes(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return authorizationState.getAuthorizationScopes(
        ownerType, ownerId, resourceType, permissionType);
  }

  /**
   * Get authorization scopes for a specific entity including inherited scopes.
   *
   * <p>This includes:
   *
   * <ul>
   *   <li>Direct authorization scopes assigned to the entity
   *   <li>Authorization scopes inherited via roles the entity belongs to
   *   <li>Authorization scopes inherited via groups the entity belongs to
   *   <li>Authorization scopes inherited via roles that groups belong to
   * </ul>
   *
   * @param authorizationClaims the authorization claims map
   * @param ownerType the type of the entity (user, client, mapping rule, etc.)
   * @param ownerId the ID of the entity
   * @param resourceType the resource type to check permissions for
   * @param permissionType the permission type to check
   * @return a stream of authorization scopes
   */
  public Stream<AuthorizationScope> getScopesForEntity(
      final Map<String, Object> authorizationClaims,
      final EntityType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final var authorizationOwnerType = convertToAuthorizationOwnerType(ownerType);

    final var direct =
        getDirectScopes(authorizationOwnerType, ownerId, resourceType, permissionType).stream();
    final var viaRole =
        membershipState.getMemberships(ownerType, ownerId, RelationType.ROLE).stream()
            .flatMap(
                roleId ->
                    getDirectScopes(
                        AuthorizationOwnerType.ROLE, roleId, resourceType, permissionType)
                        .stream());
    final var viaGroups =
        claimsExtractor.getGroups(authorizationClaims, ownerType, ownerId).stream()
            .<AuthorizationScope>mapMulti(
                (groupId, stream) -> {
                  getDirectScopes(
                          AuthorizationOwnerType.GROUP, groupId, resourceType, permissionType)
                      .forEach(stream);
                  membershipState
                      .getMemberships(EntityType.GROUP, groupId, RelationType.ROLE)
                      .stream()
                      .flatMap(
                          roleId ->
                              getDirectScopes(
                                  AuthorizationOwnerType.ROLE, roleId, resourceType, permissionType)
                                  .stream())
                      .forEach(stream);
                });
    return Stream.concat(direct, Stream.concat(viaRole, viaGroups));
  }

  private AuthorizationOwnerType convertToAuthorizationOwnerType(final EntityType entityType) {
    return switch (entityType) {
      case GROUP -> AuthorizationOwnerType.GROUP;
      case ROLE -> AuthorizationOwnerType.ROLE;
      case USER -> AuthorizationOwnerType.USER;
      case MAPPING_RULE -> AuthorizationOwnerType.MAPPING_RULE;
      case CLIENT -> AuthorizationOwnerType.CLIENT;
      case UNSPECIFIED -> AuthorizationOwnerType.UNSPECIFIED;
    };
  }

  private Stream<PersistedMappingRule> getPersistedMappingRules(
      final AuthorizationRequest request) {
    final var claims = claimsExtractor.getTokenClaims(request.claims());
    return MappingRuleMatcher.matchingRules(mappingRuleState.getAll().stream(), claims);
  }
}
