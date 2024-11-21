/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
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
            1L, AuthorizationResourceType.DEPLOYMENT, PermissionType.CREATE);
    // then
    assertThat(persistedAuth).isEmpty();
  }

  @Test
  void shouldCreatePermissions() {
    // given
    final var ownerKey = 1L;
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    final var resourceIds = Set.of("foo", "bar");

    // when
    authorizationState.createOrAddPermission(ownerKey, resourceType, permissionType, resourceIds);

    // then
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(ownerKey, resourceType, permissionType);
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder("foo", "bar");
  }

  @Test
  void shouldUpdatePermissionsIfAlreadyExists() {
    // given
    final var ownerKey = 1L;
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    final var resourceIds = Set.of("foo", "bar");
    authorizationState.createOrAddPermission(ownerKey, resourceType, permissionType, resourceIds);

    // when
    authorizationState.createOrAddPermission(ownerKey, resourceType, permissionType, Set.of("baz"));

    // then
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(ownerKey, resourceType, permissionType);
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder("foo", "bar", "baz");
  }

  @Test
  void shouldStorePermissionsByOwnerKey() {
    // given
    final var ownerKey1 = 1L;
    final var ownerKey2 = 2L;
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    authorizationState.createOrAddPermission(
        ownerKey1, resourceType, permissionType, Set.of("foo"));
    authorizationState.createOrAddPermission(
        ownerKey2, resourceType, permissionType, Set.of("bar"));

    // when
    final var resourceIds1 =
        authorizationState.getResourceIdentifiers(ownerKey1, resourceType, permissionType);
    final var resourceIds2 =
        authorizationState.getResourceIdentifiers(ownerKey2, resourceType, permissionType);

    // then
    assertThat(resourceIds1).isNotEqualTo(resourceIds2);
  }

  @Test
  void shouldStorePermissionsByResourceType() {
    // given
    final var ownerKey = 1L;
    final var resourceType1 = AuthorizationResourceType.DEPLOYMENT;
    final var resourceType2 = AuthorizationResourceType.PROCESS_DEFINITION;
    final var permissionType = PermissionType.CREATE;
    authorizationState.createOrAddPermission(
        ownerKey, resourceType1, permissionType, Set.of("foo"));
    authorizationState.createOrAddPermission(
        ownerKey, resourceType2, permissionType, Set.of("bar"));

    // when
    final var resourceIds1 =
        authorizationState.getResourceIdentifiers(ownerKey, resourceType1, permissionType);
    final var resourceIds2 =
        authorizationState.getResourceIdentifiers(ownerKey, resourceType2, permissionType);

    // then
    assertThat(resourceIds1).isNotEqualTo(resourceIds2);
  }

  @Test
  void shouldStorePermissionsByPermissionType() {
    // given
    final var ownerKey = 1L;
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType1 = PermissionType.CREATE;
    final var permissionType2 = PermissionType.UPDATE;
    authorizationState.createOrAddPermission(
        ownerKey, resourceType, permissionType1, Set.of("foo"));
    authorizationState.createOrAddPermission(
        ownerKey, resourceType, permissionType2, Set.of("bar"));

    // when
    final var resourceIds1 =
        authorizationState.getResourceIdentifiers(ownerKey, resourceType, permissionType1);
    final var resourceIds2 =
        authorizationState.getResourceIdentifiers(ownerKey, resourceType, permissionType2);

    // then
    assertThat(resourceIds1).isNotEqualTo(resourceIds2);
  }

  @Test
  void shouldRemovePermissionsResourceIdentifiers() {
    // given
    final var ownerKey = 1L;
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    final var resourceIds = List.of("foo", "bar");
    authorizationState.createOrAddPermission(ownerKey, resourceType, permissionType, resourceIds);

    // when
    authorizationState.removePermission(ownerKey, resourceType, permissionType, List.of("bar"));

    // then
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(ownerKey, resourceType, permissionType);
    assertThat(resourceIdentifiers).containsExactlyInAnyOrder("foo");
  }

  @Test
  void shouldInsertOwnerTypeByKey() {
    // given
    final var ownerKey = 1L;
    final var ownerType = AuthorizationOwnerType.USER;

    // when
    authorizationState.insertOwnerTypeByKey(ownerKey, ownerType);

    // then
    final var persistedOwnerType = authorizationState.getOwnerType(ownerKey);
    assertThat(persistedOwnerType).contains(ownerType);
  }

  @Test
  void shouldNotInsertOwnerTypeByKeyTwice() {
    // given
    final var ownerKey = 1L;
    final var ownerType = AuthorizationOwnerType.USER;

    // when
    authorizationState.insertOwnerTypeByKey(ownerKey, ownerType);

    // then
    assertThatThrownBy(() -> authorizationState.insertOwnerTypeByKey(ownerKey, ownerType))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining(
            "Key DbLong{1} in ColumnFamily OWNER_TYPE_BY_OWNER_KEY already exists");
  }

  @Test
  void shouldRemoveOwnerTypeByKey() {
    // given
    final var ownerKey = 1L;
    final var ownerType = AuthorizationOwnerType.USER;
    authorizationState.insertOwnerTypeByKey(ownerKey, ownerType);

    // when
    authorizationState.deleteOwnerTypeByKey(ownerKey);

    // then
    final var persistedOwnerType = authorizationState.getOwnerType(ownerKey);
    assertThat(persistedOwnerType).isEmpty();
  }

  @Test
  void shouldDeleteAuthorizationsByOwnerKeyPrefix() {
    // given
    final var ownerKey1 = 1L;
    final var ownerKey2 = 2L;
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = "foo";
    final var resourceId2 = "bar";
    authorizationState.createOrAddPermission(
        ownerKey1, resourceType, permissionType, Set.of(resourceId1));
    authorizationState.createOrAddPermission(
        ownerKey2, resourceType, permissionType, Set.of(resourceId2));

    // when
    authorizationState.deleteAuthorizationsByOwnerKeyPrefix(ownerKey1);

    // then
    assertThat(authorizationState.getResourceIdentifiers(ownerKey1, resourceType, permissionType))
        .isEmpty();
    assertThat(authorizationState.getResourceIdentifiers(ownerKey2, resourceType, permissionType))
        .isNotEmpty();
    assertThat(authorizationState.getAuthorizationKeysByResourceId(resourceId1)).isEmpty();
    assertThat(authorizationState.getAuthorizationKeysByResourceId(resourceId2))
        .containsExactly(
            new AuthorizationKey(ownerKey2, resourceType.toString(), permissionType.toString()));
  }

  @Test
  void shouldRemoveSinglePermissionsByOwnerKey() {
    // given
    final var ownerKey = 1L;
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = "foo";
    final var resourceId2 = "bar";
    authorizationState.createOrAddPermission(
        ownerKey, resourceType, permissionType, Set.of(resourceId1, resourceId2));

    // when
    authorizationState.removePermission(
        ownerKey, resourceType, permissionType, Set.of(resourceId1));

    // then
    assertThat(authorizationState.getResourceIdentifiers(ownerKey, resourceType, permissionType))
        .containsOnly(resourceId2);
    assertThat(authorizationState.getAuthorizationKeysByResourceId(resourceId1)).isEmpty();
    assertThat(authorizationState.getAuthorizationKeysByResourceId(resourceId2))
        .containsExactly(
            new AuthorizationKey(ownerKey, resourceType.toString(), permissionType.toString()));
  }

  @Test
  void shouldRemoveAllPermissionsByOwnerKey() {
    // given
    final var ownerKey = 1L;
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    final var resourceId1 = "foo";
    final var resourceId2 = "bar";
    authorizationState.createOrAddPermission(
        ownerKey, resourceType, permissionType, Set.of(resourceId1, resourceId2));

    // when
    authorizationState.removePermission(
        ownerKey, resourceType, permissionType, Set.of("foo", "bar"));

    // then
    assertThat(authorizationState.getResourceIdentifiers(ownerKey, resourceType, permissionType))
        .isEmpty();
    assertThat(authorizationState.getAuthorizationKeysByResourceId(resourceId1)).isEmpty();
    assertThat(authorizationState.getAuthorizationKeysByResourceId(resourceId2)).isEmpty();
  }
}
