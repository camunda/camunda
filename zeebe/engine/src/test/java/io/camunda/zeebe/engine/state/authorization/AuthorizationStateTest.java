/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class AuthorizationStateTest {
  private MutableProcessingState processingState;
  private MutableAuthorizationState authorizationState;

  @BeforeEach
  public void setup() {
    authorizationState = processingState.getAuthorizationState();
  }

  @DisplayName("should return empty list if no authorization for owner and resource is not exist")
  @Test
  void shouldReturnEmptyListIfNoAuthorizationForOwnerAndResourceExists() {
    // when
    final var persistedAuth =
        authorizationState.getResourceIdentifiers(
            AuthorizationOwnerType.USER,
            "test",
            AuthorizationResourceType.RESOURCE,
            PermissionType.CREATE);
    // then
    assertThat(persistedAuth).isEmpty();
  }

  @Test
  void shouldCreateAuthorization() {
    // given
    final var authorizationKey = 1L;
    final String ownerId = "ownerId";
    final AuthorizationOwnerType ownerType = AuthorizationOwnerType.USER;
    final String resourceId = "resourceId";
    final AuthorizationResourceType resourceType = AuthorizationResourceType.RESOURCE;
    final Set<PermissionType> permissions = Set.of(PermissionType.CREATE, PermissionType.DELETE);
    final var authorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey)
            .setOwnerId(ownerId)
            .setOwnerType(ownerType)
            .setResourceId(resourceId)
            .setResourceType(resourceType)
            .setAuthorizationPermissions(permissions);

    // when
    authorizationState.create(authorizationKey, authorizationRecord);

    // then
    final var persistedAuthorization = authorizationState.get(authorizationKey);
    assertThat(persistedAuthorization).isPresent();
    final var authorization = persistedAuthorization.get();
    assertThat(authorization.getAuthorizationKey()).isEqualTo(authorizationKey);
    assertThat(authorization.getOwnerId()).isEqualTo(ownerId);
    assertThat(authorization.getOwnerType()).isEqualTo(ownerType);
    assertThat(authorization.getResourceId()).isEqualTo(resourceId);
    assertThat(authorization.getResourceType()).isEqualTo(resourceType);
    assertThat(authorization.getPermissions()).containsExactlyInAnyOrderElementsOf(permissions);
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.CREATE);
    assertThat(resourceIdentifiers).containsExactly("resourceId");
  }

  @Test
  void shouldCreatePermissions() {
    // given
    final var ownerType = AuthorizationOwnerType.USER;
    final var ownerId = "test";
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceIds = Set.of("foo", "bar");

    // when
    authorizationState.createOrAddPermission(
        ownerType, ownerId, resourceType, permissionType, resourceIds);

    // then
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(ownerType, ownerId, resourceType, permissionType);
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder("foo", "bar");
  }

  @Test
  void shouldUpdatePermissionsIfAlreadyExists() {
    // given
    final var ownerType = AuthorizationOwnerType.USER;
    final var ownerId = "test";
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceIds = Set.of("foo", "bar");
    authorizationState.createOrAddPermission(
        ownerType, ownerId, resourceType, permissionType, resourceIds);

    // when
    authorizationState.createOrAddPermission(
        ownerType, ownerId, resourceType, permissionType, Set.of("baz"));

    // then
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(ownerType, ownerId, resourceType, permissionType);
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder("foo", "bar", "baz");
  }

  @Test
  void shouldStorePermissionsByOwnerKey() {
    // given
    final var ownerType1 = AuthorizationOwnerType.USER;
    final var ownerId1 = "test1";
    final var ownerType2 = AuthorizationOwnerType.USER;
    final var ownerId2 = "test2";
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    authorizationState.createOrAddPermission(
        ownerType1, ownerId1, resourceType, permissionType, Set.of("foo"));
    authorizationState.createOrAddPermission(
        ownerType2, ownerId2, resourceType, permissionType, Set.of("bar"));

    // when
    final var resourceIds1 =
        authorizationState.getResourceIdentifiers(
            ownerType1, ownerId1, resourceType, permissionType);
    final var resourceIds2 =
        authorizationState.getResourceIdentifiers(
            ownerType2, ownerId2, resourceType, permissionType);

    // then
    assertThat(resourceIds1).isNotEqualTo(resourceIds2);
  }

  @Test
  void shouldStorePermissionsByResourceType() {
    // given
    final var ownerType = AuthorizationOwnerType.USER;
    final var ownerId = "test";
    final var resourceType1 = AuthorizationResourceType.RESOURCE;
    final var resourceType2 = AuthorizationResourceType.PROCESS_DEFINITION;
    final var permissionType = PermissionType.CREATE;
    authorizationState.createOrAddPermission(
        ownerType, ownerId, resourceType1, permissionType, Set.of("foo"));
    authorizationState.createOrAddPermission(
        ownerType, ownerId, resourceType2, permissionType, Set.of("bar"));

    // when
    final var resourceIds1 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType1, permissionType);
    final var resourceIds2 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType2, permissionType);

    // then
    assertThat(resourceIds1).isNotEqualTo(resourceIds2);
  }

  @Test
  void shouldStorePermissionsByPermissionType() {
    // given
    final var ownerType = AuthorizationOwnerType.USER;
    final var ownerId = "test";
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType1 = PermissionType.CREATE;
    final var permissionType2 = PermissionType.UPDATE;
    authorizationState.createOrAddPermission(
        ownerType, ownerId, resourceType, permissionType1, Set.of("foo"));
    authorizationState.createOrAddPermission(
        ownerType, ownerId, resourceType, permissionType2, Set.of("bar"));

    // when
    final var resourceIds1 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, permissionType1);
    final var resourceIds2 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, permissionType2);

    // then
    assertThat(resourceIds1).isNotEqualTo(resourceIds2);
  }

  @Test
  void shouldDeleteAuthorizationsByOwnerTypeAndIdPrefix() {
    // given
    final var ownerType1 = AuthorizationOwnerType.USER;
    final var ownerId1 = "test1";
    final var ownerType2 = AuthorizationOwnerType.USER;
    final var ownerId2 = "test2";
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = "foo";
    final var resourceId2 = "bar";
    authorizationState.createOrAddPermission(
        ownerType1, ownerId1, resourceType, permissionType, Set.of(resourceId1));
    authorizationState.createOrAddPermission(
        ownerType2, ownerId2, resourceType, permissionType, Set.of(resourceId2));

    // when
    authorizationState.deleteAuthorizationsByOwnerTypeAndIdPrefix(ownerType1, ownerId1);

    // then
    assertThat(
            authorizationState.getResourceIdentifiers(
                ownerType1, ownerId1, resourceType, permissionType))
        .isEmpty();
    assertThat(
            authorizationState.getResourceIdentifiers(
                ownerType2, ownerId2, resourceType, permissionType))
        .containsExactly(resourceId2);
  }

  @Test
  void shouldRemoveSinglePermissionsByOwnerTypeAndID() {
    // given
    final var ownerType = AuthorizationOwnerType.USER;
    final var ownerId = "test";
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = "foo";
    final var resourceId2 = "bar";
    authorizationState.createOrAddPermission(
        ownerType, ownerId, resourceType, permissionType, Set.of(resourceId1, resourceId2));

    // when
    authorizationState.removePermission(
        ownerType, ownerId, resourceType, permissionType, Set.of(resourceId1));

    // then
    assertThat(
            authorizationState.getResourceIdentifiers(
                ownerType, ownerId, resourceType, permissionType))
        .containsOnly(resourceId2);
  }

  @Test
  void shouldRemoveAllPermissionsByOwnerTypeAndID() {
    // given
    final var ownerType = AuthorizationOwnerType.USER;
    final var ownerId = "test";
    final var resourceType = AuthorizationResourceType.RESOURCE;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = "foo";
    final var resourceId2 = "bar";
    authorizationState.createOrAddPermission(
        ownerType, ownerId, resourceType, permissionType, Set.of(resourceId1, resourceId2));

    // when
    authorizationState.removePermission(
        ownerType, ownerId, resourceType, permissionType, Set.of("foo", "bar"));

    // then
    assertThat(
            authorizationState.getResourceIdentifiers(
                ownerType, ownerId, resourceType, permissionType))
        .isEmpty();
  }

  @Test
  void shouldDeleteAuthorization() {
    // given
    final var authorizationKey = 1L;
    final String ownerId = "ownerId";
    final AuthorizationOwnerType ownerType = AuthorizationOwnerType.USER;
    final String resourceId = "resourceId";
    final AuthorizationResourceType resourceType = AuthorizationResourceType.RESOURCE;
    final Set<PermissionType> permissions = Set.of(PermissionType.CREATE, PermissionType.DELETE);
    final var authorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey)
            .setOwnerId(ownerId)
            .setOwnerType(ownerType)
            .setResourceId(resourceId)
            .setResourceType(resourceType)
            .setAuthorizationPermissions(permissions);

    authorizationState.create(authorizationKey, authorizationRecord);

    // when
    authorizationState.delete(authorizationKey);

    // then
    assertThat(authorizationState.get(authorizationKey)).isEmpty();

    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.CREATE);
    assertThat(resourceIdentifiers).isEmpty();

    final var resourceIdentifiers2 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.DELETE);
    assertThat(resourceIdentifiers2).isEmpty();
  }

  @Test
  void shouldDeleteAuthorizationWithMultipleRecords() {
    // given
    final var authorizationKey1 = 1L;
    final String ownerId1 = "ownerId1";
    final AuthorizationOwnerType ownerType1 = AuthorizationOwnerType.USER;
    final String resourceId1 = "resourceId1";
    final AuthorizationResourceType resourceType1 = AuthorizationResourceType.RESOURCE;
    final Set<PermissionType> permissions1 = Set.of(PermissionType.CREATE, PermissionType.DELETE);
    final var authorizationRecord1 =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey1)
            .setOwnerId(ownerId1)
            .setOwnerType(ownerType1)
            .setResourceId(resourceId1)
            .setResourceType(resourceType1)
            .setAuthorizationPermissions(permissions1);

    final var authorizationKey2 = 2L;
    final String ownerId2 = "ownerId2";
    final AuthorizationOwnerType ownerType2 = AuthorizationOwnerType.USER;
    final String resourceId2 = "resourceId2";
    final AuthorizationResourceType resourceType2 = AuthorizationResourceType.RESOURCE;
    final Set<PermissionType> permissions2 = Set.of(PermissionType.CREATE, PermissionType.DELETE);
    final var authorizationRecord2 =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey2)
            .setOwnerId(ownerId2)
            .setOwnerType(ownerType2)
            .setResourceId(resourceId2)
            .setResourceType(resourceType2)
            .setAuthorizationPermissions(permissions2);

    authorizationState.create(authorizationKey1, authorizationRecord1);
    authorizationState.create(authorizationKey2, authorizationRecord2);

    // when
    authorizationState.delete(authorizationKey1);

    // then
    assertThat(authorizationState.get(authorizationKey1)).isEmpty();

    final var resourceIdentifiers1 =
        authorizationState.getResourceIdentifiers(
            ownerType1, ownerId1, resourceType1, PermissionType.CREATE);
    assertThat(resourceIdentifiers1).isEmpty();

    final var resourceIdentifiers2 =
        authorizationState.getResourceIdentifiers(
            ownerType1, ownerId1, resourceType1, PermissionType.DELETE);
    assertThat(resourceIdentifiers2).isEmpty();

    final var resourceIdentifiers3 =
        authorizationState.getResourceIdentifiers(
            ownerType2, ownerId2, resourceType2, PermissionType.CREATE);
    assertThat(resourceIdentifiers3).containsExactly(resourceId2);

    final var resourceIdentifiers4 =
        authorizationState.getResourceIdentifiers(
            ownerType2, ownerId2, resourceType2, PermissionType.DELETE);
    assertThat(resourceIdentifiers4).containsExactly(resourceId2);
  }
}
