/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.zeebe.engine.state.appliers.AuthorizationCreatedApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class AuthorizationScopeStateAdapterTest {

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState processingState;

  private AuthorizationScopeStateAdapter adapter;
  private AuthorizationCreatedApplier authorizationCreatedApplier;
  private final Random random = new Random();

  @BeforeEach
  void setup() {
    adapter = new AuthorizationScopeStateAdapter(processingState.getAuthorizationState());
    authorizationCreatedApplier =
        new AuthorizationCreatedApplier(processingState.getAuthorizationState());
  }

  @Test
  void shouldReturnScopeForOwner() {
    // given
    final var userId = "user1";
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        userId,
        AuthorizationOwnerType.USER,
        io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION,
        io.camunda.zeebe.protocol.record.value.PermissionType.READ,
        resourceId);

    // when
    final var scopes =
        adapter.findAuthorizedScopes(
            Map.of(EntityType.USER, Set.of(userId)),
            AuthorizationResourceType.PROCESS_DEFINITION,
            PermissionType.READ);

    // then
    assertThat(scopes).containsExactly(AuthorizationScope.id(resourceId));
  }

  @Test
  void shouldReturnEmptyScopesWhenNoPermission() {
    // when
    final var scopes =
        adapter.findAuthorizedScopes(
            Map.of(EntityType.USER, Set.of("no-such-user")),
            AuthorizationResourceType.PROCESS_DEFINITION,
            PermissionType.READ);

    // then
    assertThat(scopes).isEmpty();
  }

  @Test
  void shouldAggregateScopesAcrossMultipleOwners() {
    // given
    final var resourceId1 = UUID.randomUUID().toString();
    final var resourceId2 = UUID.randomUUID().toString();
    addPermission(
        "user1",
        AuthorizationOwnerType.USER,
        io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION,
        io.camunda.zeebe.protocol.record.value.PermissionType.READ,
        resourceId1);
    addPermission(
        "role1",
        AuthorizationOwnerType.ROLE,
        io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION,
        io.camunda.zeebe.protocol.record.value.PermissionType.READ,
        resourceId2);

    // when
    final var scopes =
        adapter.findAuthorizedScopes(
            Map.of(EntityType.USER, Set.of("user1"), EntityType.ROLE, Set.of("role1")),
            AuthorizationResourceType.PROCESS_DEFINITION,
            PermissionType.READ);

    // then
    assertThat(scopes)
        .containsExactlyInAnyOrder(
            AuthorizationScope.id(resourceId1), AuthorizationScope.id(resourceId2));
  }

  @Test
  void shouldReturnTrueForHasAuthorizedScopeWithMatchingId() {
    // given
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        "user1",
        AuthorizationOwnerType.USER,
        io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION,
        io.camunda.zeebe.protocol.record.value.PermissionType.READ,
        resourceId);

    // when / then
    assertThat(
            adapter.hasAuthorizedScope(
                Map.of(EntityType.USER, Set.of("user1")),
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.READ,
                List.of(resourceId)))
        .isTrue();
  }

  @Test
  void shouldReturnFalseForHasAuthorizedScopeWithNonMatchingId() {
    // given
    addPermission(
        "user1",
        AuthorizationOwnerType.USER,
        io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION,
        io.camunda.zeebe.protocol.record.value.PermissionType.READ,
        UUID.randomUUID().toString());

    // when / then
    assertThat(
            adapter.hasAuthorizedScope(
                Map.of(EntityType.USER, Set.of("user1")),
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.READ,
                List.of("different-resource-id")))
        .isFalse();
  }

  @Test
  void shouldReturnPermissionTypesForMatchingResource() {
    // given
    final var resourceId = UUID.randomUUID().toString();
    addPermission(
        "user1",
        AuthorizationOwnerType.USER,
        io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION,
        io.camunda.zeebe.protocol.record.value.PermissionType.READ,
        resourceId);
    addPermission(
        "user1",
        AuthorizationOwnerType.USER,
        io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION,
        io.camunda.zeebe.protocol.record.value.PermissionType.CREATE_PROCESS_INSTANCE,
        resourceId);

    // when
    final var permTypes =
        adapter.findPermissionTypes(
            Map.of(EntityType.USER, Set.of("user1")),
            AuthorizationResourceType.PROCESS_DEFINITION,
            List.of(resourceId));

    // then
    assertThat(permTypes)
        .containsExactlyInAnyOrder(PermissionType.READ, PermissionType.CREATE_PROCESS_INSTANCE);
  }

  @Test
  void shouldReturnEmptyPermissionTypesWhenResourceIdDoesNotMatch() {
    // given
    addPermission(
        "user1",
        AuthorizationOwnerType.USER,
        io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION,
        io.camunda.zeebe.protocol.record.value.PermissionType.READ,
        UUID.randomUUID().toString());

    // when
    final var permTypes =
        adapter.findPermissionTypes(
            Map.of(EntityType.USER, Set.of("user1")),
            AuthorizationResourceType.PROCESS_DEFINITION,
            List.of("different-resource-id"));

    // then
    assertThat(permTypes).isEmpty();
  }

  @Test
  void shouldMatchWildcardScopeForAnyResourceId() {
    // given — wildcard authorization
    final var authKey = random.nextLong();
    final var auth =
        new AuthorizationRecord()
            .setAuthorizationKey(authKey)
            .setOwnerId("user1")
            .setOwnerType(AuthorizationOwnerType.USER)
            .setResourceMatcher(AuthorizationResourceMatcher.ANY)
            .setResourceId("*")
            .setResourcePropertyName("")
            .setResourceType(
                io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.PROCESS_DEFINITION)
            .setPermissionTypes(Set.of(io.camunda.zeebe.protocol.record.value.PermissionType.READ));
    authorizationCreatedApplier.applyState(authKey, auth);

    // when / then
    assertThat(
            adapter.hasAuthorizedScope(
                Map.of(EntityType.USER, Set.of("user1")),
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.READ,
                List.of("any-process-id")))
        .isTrue();
  }

  // --- helpers ---

  private void addPermission(
      final String ownerId,
      final AuthorizationOwnerType ownerType,
      final io.camunda.zeebe.protocol.record.value.AuthorizationResourceType resourceType,
      final io.camunda.zeebe.protocol.record.value.PermissionType permissionType,
      final String resourceId) {
    final var authKey = random.nextLong();
    final var auth =
        new AuthorizationRecord()
            .setAuthorizationKey(authKey)
            .setOwnerId(ownerId)
            .setOwnerType(ownerType)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId)
            .setResourcePropertyName("")
            .setResourceType(resourceType)
            .setPermissionTypes(Set.of(permissionType));
    authorizationCreatedApplier.applyState(authKey, auth);
  }
}
