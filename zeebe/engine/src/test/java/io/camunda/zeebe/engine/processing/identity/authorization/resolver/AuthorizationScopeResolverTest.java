/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.resolver;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthorizationScopeResolverTest {

  private AuthorizationState authorizationState;
  private MembershipState membershipState;
  private MappingRuleState mappingRuleState;
  private ClaimsExtractor claimsExtractor;
  private AuthorizationScopeResolver scopeResolver;

  @BeforeEach
  void setUp() {
    authorizationState = mock(AuthorizationState.class);
    membershipState = mock(MembershipState.class);
    mappingRuleState = mock(MappingRuleState.class);
    claimsExtractor = new ClaimsExtractor(membershipState);

    scopeResolver =
        new AuthorizationScopeResolver(
            authorizationState, membershipState, mappingRuleState, claimsExtractor, true);
  }

  @Test
  void shouldReturnWildcardWhenAuthorizationsDisabled() {
    // given - authorizations disabled resolver
    final var resolverWithoutAuth =
        new AuthorizationScopeResolver(
            authorizationState, membershipState, mappingRuleState, claimsExtractor, false);
    final var request = mock(AuthorizationRequest.class);

    // when
    final var scopes = resolverWithoutAuth.getAllAuthorizedScopes(request);

    // then
    assertThat(scopes).containsExactly(AuthorizationScope.WILDCARD);
  }

  @Test
  void shouldReturnWildcardForAnonymousUser() {
    // given - anonymous user request
    final var request = mock(AuthorizationRequest.class);
    when(request.claims()).thenReturn(Map.of(AUTHORIZED_ANONYMOUS_USER, true));

    // when
    final var scopes = scopeResolver.getAllAuthorizedScopes(request);

    // then
    assertThat(scopes).containsExactly(AuthorizationScope.WILDCARD);
  }

  @Test
  void shouldGetAllAuthorizedScopesForUser() {
    // given - user with direct authorization scope
    final var username = "demo-user";
    final var resourceType = AuthorizationResourceType.PROCESS_DEFINITION;
    final var permissionType = PermissionType.CREATE;
    final var scope = AuthorizationScope.of("process-1");

    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.USER, username, resourceType, permissionType))
        .thenReturn(Set.of(scope));

    final var request = mockAuthorizationRequestForUsername(username, resourceType, permissionType);

    // when
    final var scopes = scopeResolver.getAllAuthorizedScopes(request);

    // then
    assertThat(scopes).containsExactly(scope);
  }

  @Test
  void shouldGetAllAuthorizedScopesForClient() {
    // given - client with direct authorization scope
    final var clientId = "client-1";
    final var resourceType = AuthorizationResourceType.PROCESS_DEFINITION;
    final var permissionType = PermissionType.READ;
    final var scope = AuthorizationScope.WILDCARD;

    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.CLIENT, clientId, resourceType, permissionType))
        .thenReturn(Set.of(scope));

    final var request = mockAuthorizationRequestForClient(clientId, resourceType, permissionType);

    // when
    final var scopes = scopeResolver.getAllAuthorizedScopes(request);

    // then
    assertThat(scopes).containsExactly(scope);
  }

  @Test
  void shouldPreferClientOverUsername() {
    // given - both client and username in claims, client has scopes
    final var username = "demo-user";
    final var clientId = "client-1";
    final var resourceType = AuthorizationResourceType.PROCESS_DEFINITION;
    final var permissionType = PermissionType.CREATE;
    final var clientScope = AuthorizationScope.of("process-client");
    final var userScope = AuthorizationScope.of("process-user");

    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.CLIENT, clientId, resourceType, permissionType))
        .thenReturn(Set.of(clientScope));
    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.USER, username, resourceType, permissionType))
        .thenReturn(Set.of(userScope));

    final var request = mock(AuthorizationRequest.class);
    when(request.claims())
        .thenReturn(Map.of(AUTHORIZED_USERNAME, username, AUTHORIZED_CLIENT_ID, clientId));
    when(request.resourceType()).thenReturn(resourceType);
    when(request.permissionType()).thenReturn(permissionType);

    // when
    final var scopes = scopeResolver.getAllAuthorizedScopes(request);

    // then - should only use client, not username
    assertThat(scopes).containsExactly(clientScope);
  }

  @Test
  void shouldReturnEmptyScopesWhenNoAuthorizationsFound() {
    // given - user with no authorization scopes
    final var username = "demo-user";
    final var resourceType = AuthorizationResourceType.PROCESS_DEFINITION;
    final var permissionType = PermissionType.CREATE;

    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.USER, username, resourceType, permissionType))
        .thenReturn(Set.of());

    final var request = mockAuthorizationRequestForUsername(username, resourceType, permissionType);

    // when
    final var scopes = scopeResolver.getAllAuthorizedScopes(request);

    // then
    assertThat(scopes).isEmpty();
  }

  @Test
  void shouldGetDirectScopesForUser() {
    // given - user with direct authorization scope
    final var username = "demo-user";
    final var resourceType = AuthorizationResourceType.PROCESS_DEFINITION;
    final var permissionType = PermissionType.CREATE;
    final var scope = AuthorizationScope.of("process-1");

    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.USER, username, resourceType, permissionType))
        .thenReturn(Set.of(scope));

    // when
    final var scopes =
        scopeResolver.getDirectScopes(
            AuthorizationOwnerType.USER, username, resourceType, permissionType);

    // then
    assertThat(scopes).containsExactly(scope);
  }

  @Test
  void shouldResolveScopesViaRole() {
    // given - user assigned to role, role has authorization scope
    final var username = "demo-user";
    final var roleId = "role-1";
    final var resourceType = AuthorizationResourceType.PROCESS_DEFINITION;
    final var permissionType = PermissionType.UPDATE;
    final var roleScope = AuthorizationScope.of("process-2");

    when(membershipState.getMemberships(EntityType.USER, username, RelationType.ROLE))
        .thenReturn(List.of(roleId));
    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.ROLE, roleId, resourceType, permissionType))
        .thenReturn(Set.of(roleScope));

    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);

    // when
    final var scopes =
        scopeResolver
            .getScopesForEntity(claims, EntityType.USER, username, resourceType, permissionType)
            .toList();

    // then
    assertThat(scopes).containsExactly(roleScope);
  }

  @Test
  void shouldResolveScopesViaGroup() {
    // given - user assigned to group, group has authorization scope
    final var username = "demo-user";
    final var groupId = "group-1";
    final var resourceType = AuthorizationResourceType.DECISION_DEFINITION;
    final var permissionType = PermissionType.READ;
    final var groupScope = AuthorizationScope.of("decision-1");

    when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
        .thenReturn(List.of(groupId));
    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.GROUP, groupId, resourceType, permissionType))
        .thenReturn(Set.of(groupScope));

    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);

    // when
    final var scopes =
        scopeResolver
            .getScopesForEntity(claims, EntityType.USER, username, resourceType, permissionType)
            .toList();

    // then
    assertThat(scopes).containsExactly(groupScope);
  }

  @Test
  void shouldResolveScopesViaGroupRole() {
    // given - user in group, group has role, role has authorization scope
    final var username = "demo-user";
    final var groupId = "group-1";
    final var roleId = "role-1";
    final var resourceType = AuthorizationResourceType.PROCESS_DEFINITION;
    final var permissionType = PermissionType.CREATE;
    final var roleScope = AuthorizationScope.of("deployment-1");

    when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
        .thenReturn(List.of(groupId));
    when(membershipState.getMemberships(EntityType.GROUP, groupId, RelationType.ROLE))
        .thenReturn(List.of(roleId));
    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.ROLE, roleId, resourceType, permissionType))
        .thenReturn(Set.of(roleScope));

    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);

    // when
    final var scopes =
        scopeResolver
            .getScopesForEntity(claims, EntityType.USER, username, resourceType, permissionType)
            .toList();

    // then
    assertThat(scopes).containsExactly(roleScope);
  }

  @Test
  void shouldCombineScopesFromMultipleSources() {
    // given - user has direct scope, role scope, and group scope
    final var username = "demo-user";
    final var roleId = "role-1";
    final var groupId = "group-1";
    final var resourceType = AuthorizationResourceType.PROCESS_DEFINITION;
    final var permissionType = PermissionType.READ;

    final var directScope = AuthorizationScope.of("process-1");
    final var roleScope = AuthorizationScope.of("process-2");
    final var groupScope = AuthorizationScope.of("process-3");

    // User memberships
    when(membershipState.getMemberships(EntityType.USER, username, RelationType.ROLE))
        .thenReturn(List.of(roleId));
    when(membershipState.getMemberships(EntityType.USER, username, RelationType.GROUP))
        .thenReturn(List.of(groupId));

    // Direct scopes
    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.USER, username, resourceType, permissionType))
        .thenReturn(Set.of(directScope));

    // Role scopes
    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.ROLE, roleId, resourceType, permissionType))
        .thenReturn(Set.of(roleScope));

    // Group scopes
    when(authorizationState.getAuthorizationScopes(
            AuthorizationOwnerType.GROUP, groupId, resourceType, permissionType))
        .thenReturn(Set.of(groupScope));

    final var claims = Map.<String, Object>of(AUTHORIZED_USERNAME, username);

    // when
    final var scopes =
        scopeResolver
            .getScopesForEntity(claims, EntityType.USER, username, resourceType, permissionType)
            .toList();

    // then
    assertThat(scopes).containsExactlyInAnyOrder(directScope, roleScope, groupScope);
  }

  // Helper methods
  private AuthorizationRequest mockAuthorizationRequestForUsername(
      final String username,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final var request = mock(AuthorizationRequest.class);
    when(request.claims()).thenReturn(Map.of(AUTHORIZED_USERNAME, username));
    when(request.resourceType()).thenReturn(resourceType);
    when(request.permissionType()).thenReturn(permissionType);
    return request;
  }

  private AuthorizationRequest mockAuthorizationRequestForClient(
      final String clientId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final var request = mock(AuthorizationRequest.class);
    when(request.claims()).thenReturn(Map.of(AUTHORIZED_CLIENT_ID, clientId));
    when(request.resourceType()).thenReturn(resourceType);
    when(request.permissionType()).thenReturn(permissionType);
    return request;
  }
}
