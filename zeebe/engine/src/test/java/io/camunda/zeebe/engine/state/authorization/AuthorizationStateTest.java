/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
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
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId)
            .setResourceType(resourceType)
            .setPermissionTypes(permissions);

    // when
    authorizationState.create(authorizationKey, authorizationRecord);

    // then
    final var persistedAuthorization = authorizationState.get(authorizationKey);
    assertThat(persistedAuthorization).isPresent();
    final var authorization = persistedAuthorization.get();
    assertThat(authorization.getAuthorizationKey()).isEqualTo(authorizationKey);
    assertThat(authorization.getOwnerId()).isEqualTo(ownerId);
    assertThat(authorization.getOwnerType()).isEqualTo(ownerType);
    assertThat(authorization.getResourceMatcher()).isEqualTo(AuthorizationResourceMatcher.ID);
    assertThat(authorization.getResourceId()).isEqualTo(resourceId);
    assertThat(authorization.getResourceType()).isEqualTo(resourceType);
    assertThat(authorization.getPermissionTypes()).containsExactlyInAnyOrderElementsOf(permissions);
    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.CREATE);
    assertThat(resourceIdentifiers).containsExactly("resourceId");

    final var keys = authorizationState.getAuthorizationKeysForOwner(ownerType, ownerId);
    assertThat(keys).containsExactly(authorizationKey);
  }

  @Test
  void shouldUpdateAuthorizationChangingPermissions() {
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
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId)
            .setResourceType(resourceType)
            .setPermissionTypes(permissions);

    authorizationState.create(authorizationKey, authorizationRecord);

    // when
    final var updatedAuthorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey)
            .setOwnerId(ownerId)
            .setOwnerType(ownerType)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId("anotherResourceId")
            .setResourceType(resourceType)
            .setPermissionTypes(Set.of(PermissionType.READ, PermissionType.ACCESS));
    authorizationState.update(authorizationKey, updatedAuthorizationRecord);

    // then
    final var persistedAuthorization = authorizationState.get(authorizationKey);
    assertThat(persistedAuthorization).isPresent();
    final var authorization = persistedAuthorization.get();
    assertThat(authorization.getAuthorizationKey()).isEqualTo(authorizationKey);
    assertThat(authorization.getOwnerId()).isEqualTo(ownerId);
    assertThat(authorization.getOwnerType()).isEqualTo(ownerType);
    assertThat(authorization.getResourceMatcher()).isEqualTo(AuthorizationResourceMatcher.ID);
    assertThat(authorization.getResourceId()).isEqualTo("anotherResourceId");
    assertThat(authorization.getResourceType()).isEqualTo(resourceType);
    assertThat(authorization.getPermissionTypes())
        .containsExactlyInAnyOrderElementsOf(Set.of(PermissionType.READ, PermissionType.ACCESS));

    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.READ);
    assertThat(resourceIdentifiers).containsExactly("anotherResourceId");

    final var anotherResourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.ACCESS);
    assertThat(anotherResourceIdentifiers).containsExactly("anotherResourceId");

    final var anotherResourceIdentifiers2 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.CREATE);
    assertThat(anotherResourceIdentifiers2).isEmpty();

    final var anotherResourceIdentifiers3 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.DELETE);
    assertThat(anotherResourceIdentifiers3).isEmpty();

    final var keys = authorizationState.getAuthorizationKeysForOwner(ownerType, ownerId);
    assertThat(keys).containsExactly(authorizationKey);
  }

  @Test
  void shouldUpdateAuthorizationChangingResourceMatcherAndId() {
    // given
    final var authorizationKey = 1L;
    final String ownerId = "ownerId";
    final AuthorizationOwnerType ownerType = AuthorizationOwnerType.USER;
    final AuthorizationResourceMatcher resourceMatcher = AuthorizationResourceMatcher.ID;
    final String resourceId = "resourceId";
    final AuthorizationResourceType resourceType = AuthorizationResourceType.RESOURCE;
    final Set<PermissionType> permissions = Set.of(PermissionType.CREATE, PermissionType.DELETE);
    final var authorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey)
            .setOwnerId(ownerId)
            .setOwnerType(ownerType)
            .setResourceMatcher(resourceMatcher)
            .setResourceId(resourceId)
            .setResourceType(resourceType)
            .setPermissionTypes(permissions);

    authorizationState.create(authorizationKey, authorizationRecord);

    // when
    final var updatedAuthorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey)
            .setOwnerId(ownerId)
            .setOwnerType(ownerType)
            .setResourceMatcher(AuthorizationResourceMatcher.ANY)
            .setResourceId("*")
            .setResourceType(resourceType)
            .setPermissionTypes(Set.of(PermissionType.READ, PermissionType.ACCESS));
    authorizationState.update(authorizationKey, updatedAuthorizationRecord);

    // then
    final var persistedAuthorization = authorizationState.get(authorizationKey);
    assertThat(persistedAuthorization).isPresent();
    final var authorization = persistedAuthorization.get();
    assertThat(authorization.getAuthorizationKey()).isEqualTo(authorizationKey);
    assertThat(authorization.getOwnerId()).isEqualTo(ownerId);
    assertThat(authorization.getOwnerType()).isEqualTo(ownerType);
    assertThat(authorization.getResourceMatcher()).isEqualTo(AuthorizationResourceMatcher.ANY);
    assertThat(authorization.getResourceId()).isEqualTo("*");
    assertThat(authorization.getResourceType()).isEqualTo(resourceType);
    assertThat(authorization.getPermissionTypes())
        .containsExactlyInAnyOrderElementsOf(Set.of(PermissionType.READ, PermissionType.ACCESS));

    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.READ);
    assertThat(resourceIdentifiers).containsExactly("*");

    final var anotherResourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.ACCESS);
    assertThat(anotherResourceIdentifiers).containsExactly("*");

    final var anotherResourceIdentifiers2 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.CREATE);
    assertThat(anotherResourceIdentifiers2).isEmpty();

    final var anotherResourceIdentifiers3 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.DELETE);
    assertThat(anotherResourceIdentifiers3).isEmpty();

    final var keys = authorizationState.getAuthorizationKeysForOwner(ownerType, ownerId);
    assertThat(keys).containsExactly(authorizationKey);
  }

  @Test
  void shouldUpdateAuthorizationChangingOwnerIdAndResourceId() {
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
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId)
            .setResourceType(resourceType)
            .setPermissionTypes(permissions);

    authorizationState.create(authorizationKey, authorizationRecord);

    // when
    final var updatedAuthorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey)
            .setOwnerId("anotherOwnerId")
            .setOwnerType(ownerType)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId("anotherResourceId")
            .setResourceType(resourceType)
            .setPermissionTypes(permissions);
    authorizationState.update(authorizationKey, updatedAuthorizationRecord);

    // then
    final var persistedAuthorization = authorizationState.get(authorizationKey);
    assertThat(persistedAuthorization).isPresent();
    final var authorization = persistedAuthorization.get();
    assertThat(authorization.getAuthorizationKey()).isEqualTo(authorizationKey);
    assertThat(authorization.getOwnerId()).isEqualTo("anotherOwnerId");
    assertThat(authorization.getOwnerType()).isEqualTo(ownerType);
    assertThat(authorization.getResourceId()).isEqualTo("anotherResourceId");
    assertThat(authorization.getResourceType()).isEqualTo(resourceType);
    assertThat(authorization.getPermissionTypes()).containsExactlyInAnyOrderElementsOf(permissions);

    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType, "anotherOwnerId", resourceType, PermissionType.CREATE);
    assertThat(resourceIdentifiers).containsExactly("anotherResourceId");

    final var anotherResourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType, "anotherOwnerId", resourceType, PermissionType.DELETE);
    assertThat(anotherResourceIdentifiers).containsExactly("anotherResourceId");

    final var anotherResourceIdentifiers2 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.CREATE);
    assertThat(anotherResourceIdentifiers2).isEmpty();

    final var anotherResourceIdentifiers3 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, resourceType, PermissionType.DELETE);
    assertThat(anotherResourceIdentifiers3).isEmpty();

    final var keysByOwner = authorizationState.getAuthorizationKeysForOwner(ownerType, ownerId);
    assertThat(keysByOwner).isEmpty();
    final var keysByAnotherOwner =
        authorizationState.getAuthorizationKeysForOwner(ownerType, "anotherOwnerId");
    assertThat(keysByAnotherOwner).containsExactly(authorizationKey);
  }

  @Test
  void shouldUpdateAuthorizationChangingResourceType() {
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
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId)
            .setResourceType(resourceType)
            .setPermissionTypes(permissions);

    authorizationState.create(authorizationKey, authorizationRecord);

    // when
    final var updatedAuthorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey)
            .setOwnerId(ownerId)
            .setOwnerType(ownerType)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId)
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setPermissionTypes(permissions);
    authorizationState.update(authorizationKey, updatedAuthorizationRecord);

    // then
    final var persistedAuthorization = authorizationState.get(authorizationKey);
    assertThat(persistedAuthorization).isPresent();
    final var authorization = persistedAuthorization.get();
    assertThat(authorization.getAuthorizationKey()).isEqualTo(authorizationKey);
    assertThat(authorization.getOwnerId()).isEqualTo(ownerId);
    assertThat(authorization.getOwnerType()).isEqualTo(ownerType);
    assertThat(authorization.getResourceId()).isEqualTo(resourceId);
    assertThat(authorization.getResourceType())
        .isEqualTo(AuthorizationResourceType.PROCESS_DEFINITION);
    assertThat(authorization.getPermissionTypes()).containsExactlyInAnyOrderElementsOf(permissions);

    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType,
            ownerId,
            AuthorizationResourceType.PROCESS_DEFINITION,
            PermissionType.CREATE);
    assertThat(resourceIdentifiers).containsExactly("resourceId");

    final var anotherResourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType,
            ownerId,
            AuthorizationResourceType.PROCESS_DEFINITION,
            PermissionType.DELETE);
    assertThat(anotherResourceIdentifiers).containsExactly("resourceId");

    final var anotherResourceIdentifiers2 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, AuthorizationResourceType.RESOURCE, PermissionType.CREATE);
    assertThat(anotherResourceIdentifiers2).isEmpty();

    final var anotherResourceIdentifiers3 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, AuthorizationResourceType.RESOURCE, PermissionType.DELETE);
    assertThat(anotherResourceIdentifiers3).isEmpty();

    final var keys = authorizationState.getAuthorizationKeysForOwner(ownerType, ownerId);
    assertThat(keys).containsExactly(authorizationKey);
  }

  @Test
  void shouldUpdateAuthorizationChangingPermissionWithMultipleRecords() {
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
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId)
            .setResourceType(resourceType)
            .setPermissionTypes(permissions);

    authorizationState.create(authorizationKey, authorizationRecord);

    final var anotherRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(2L)
            .setOwnerId(ownerId)
            .setOwnerType(ownerType)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId("resourceId2")
            .setResourceType(resourceType)
            .setPermissionTypes(permissions);

    authorizationState.create(2L, anotherRecord);

    // when
    final var updatedAuthorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey)
            .setOwnerId(ownerId)
            .setOwnerType(ownerType)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId)
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setPermissionTypes(Set.of(PermissionType.READ_PROCESS_DEFINITION));
    authorizationState.update(authorizationKey, updatedAuthorizationRecord);

    // then
    final var persistedAuthorization = authorizationState.get(authorizationKey);
    assertThat(persistedAuthorization).isPresent();
    final var authorization = persistedAuthorization.get();
    assertThat(authorization.getAuthorizationKey()).isEqualTo(authorizationKey);
    assertThat(authorization.getOwnerId()).isEqualTo(ownerId);
    assertThat(authorization.getOwnerType()).isEqualTo(ownerType);
    assertThat(authorization.getResourceId()).isEqualTo(resourceId);
    assertThat(authorization.getResourceType())
        .isEqualTo(AuthorizationResourceType.PROCESS_DEFINITION);
    assertThat(authorization.getPermissionTypes())
        .containsExactlyInAnyOrderElementsOf(Set.of(PermissionType.READ_PROCESS_DEFINITION));

    final var resourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType,
            ownerId,
            AuthorizationResourceType.PROCESS_DEFINITION,
            PermissionType.READ_PROCESS_DEFINITION);
    assertThat(resourceIdentifiers).containsExactly("resourceId");

    final var anotherResourceIdentifiers =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, AuthorizationResourceType.RESOURCE, PermissionType.CREATE);
    assertThat(anotherResourceIdentifiers).containsExactly("resourceId2");

    final var anotherResourceIdentifiers2 =
        authorizationState.getResourceIdentifiers(
            ownerType, ownerId, AuthorizationResourceType.RESOURCE, PermissionType.DELETE);
    assertThat(anotherResourceIdentifiers2).containsExactly("resourceId2");

    final var keys = authorizationState.getAuthorizationKeysForOwner(ownerType, ownerId);
    assertThat(keys).containsExactly(1L, 2L);
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
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId)
            .setResourceType(resourceType)
            .setPermissionTypes(permissions);

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

    final var keys = authorizationState.getAuthorizationKeysForOwner(ownerType, ownerId);
    assertThat(keys).isEmpty();
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
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId1)
            .setResourceType(resourceType1)
            .setPermissionTypes(permissions1);

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
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId2)
            .setResourceType(resourceType2)
            .setPermissionTypes(permissions2);

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

    final var keys1 = authorizationState.getAuthorizationKeysForOwner(ownerType1, ownerId1);
    assertThat(keys1).isEmpty();
    final var keys2 = authorizationState.getAuthorizationKeysForOwner(ownerType2, ownerId2);
    assertThat(keys2).containsExactly(authorizationKey2);
  }
}
