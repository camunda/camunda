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
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.Permission;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionAction;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
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

  @DisplayName("should return null if no authorization for owner and resource is not exist")
  @Test
  void shouldReturnNullIfNoAuthorizationForOwnerAndResourceExists() {
    // when
    final var persistedAuth =
        authorizationState.getResourceIdentifiers(
            1L, AuthorizationResourceType.DEPLOYMENT, PermissionType.CREATE);
    // then
    assertThat(persistedAuth).isNull();
  }

  @DisplayName(
      "should create authorization if an authorization for the owner and resource pair does not exist")
  @Test
  void shouldCreateIfUsernameDoesNotExist() {
    // when
    final AuthorizationRecord authorizationRecord =
        new AuthorizationRecord()
            .setOwnerKey(1L)
            .setAction(PermissionAction.ADD)
            .setOwnerType(AuthorizationOwnerType.GROUP)
            .setResourceType(AuthorizationResourceType.DEPLOYMENT)
            .addPermission(
                new Permission().setPermissionType(PermissionType.CREATE).addResourceId("*"));
    authorizationState.createAuthorization(authorizationRecord);

    // then
    final var persistedAuthorization =
        authorizationState.getResourceIdentifiers(
            authorizationRecord.getOwnerKey(),
            authorizationRecord.getResourceType(),
            PermissionType.CREATE);
    assertThat(persistedAuthorization.getResourceIdentifiers())
        .isEqualTo(authorizationRecord.getPermissions().getFirst().getResourceIds());
  }

  @DisplayName(
      "should throw an exception when an authorization for owner and resource pair already exist")
  @Test
  void shouldThrowExceptionInCreateIfUsernameDoesNotExist() {
    final var ownerKey = 1L;
    // given
    final AuthorizationRecord authorizationRecord =
        new AuthorizationRecord()
            .setOwnerKey(ownerKey)
            .setAction(PermissionAction.ADD)
            .setOwnerType(AuthorizationOwnerType.GROUP)
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .addPermission(
                new Permission().setPermissionType(PermissionType.CREATE).addResourceId("*"));
    authorizationState.createAuthorization(authorizationRecord);

    // when/then
    assertThatThrownBy(() -> authorizationState.createAuthorization(authorizationRecord))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining(
            "Key DbCompositeKey{first=DbLong{"
                + ownerKey
                + "}, second=DbCompositeKey{first=PROCESS_DEFINITION, second=CREATE}} in ColumnFamily RESOURCE_IDS_BY_OWNER_KEY_RESOURCE_TYPE_AND_PERMISSION already exists");
  }

  @DisplayName("should return the correct authorization")
  @Test
  void shouldReturnCorrectAuthorization() {
    // given
    final AuthorizationRecord authorizationRecordOne =
        new AuthorizationRecord()
            .setOwnerKey(1L)
            .setAction(PermissionAction.ADD)
            .setOwnerType(AuthorizationOwnerType.GROUP)
            .setResourceType(AuthorizationResourceType.DEPLOYMENT)
            .addPermission(
                new Permission()
                    .setPermissionType(PermissionType.READ)
                    .addResourceId("bpmnProcessId:foo"));
    authorizationState.createAuthorization(authorizationRecordOne);

    final AuthorizationRecord authorizationRecordTwo =
        new AuthorizationRecord()
            .setOwnerKey(2L)
            .setAction(PermissionAction.ADD)
            .setOwnerType(AuthorizationOwnerType.GROUP)
            .setResourceType(AuthorizationResourceType.DEPLOYMENT)
            .addPermission(
                new Permission()
                    .setPermissionType(PermissionType.CREATE)
                    .addResourceId("bpmnProcessId:bar"));
    authorizationState.createAuthorization(authorizationRecordTwo);

    final var authorizationOne =
        authorizationState.getResourceIdentifiers(
            authorizationRecordOne.getOwnerKey(),
            authorizationRecordOne.getResourceType(),
            PermissionType.READ);

    final var authorizationTwo =
        authorizationState.getResourceIdentifiers(
            authorizationRecordTwo.getOwnerKey(),
            authorizationRecordTwo.getResourceType(),
            PermissionType.CREATE);

    assertThat(authorizationOne).isNotEqualTo(authorizationTwo);
    assertThat(authorizationOne.getResourceIdentifiers())
        .isEqualTo(authorizationRecordOne.getPermissions().getFirst().getResourceIds());
    assertThat(authorizationTwo.getResourceIdentifiers())
        .isEqualTo(authorizationRecordTwo.getPermissions().getFirst().getResourceIds());
  }

  @Test
  void shouldCreatePermissions() {
    // given
    final var ownerKey = 1L;
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    final var resourceIds = List.of("foo", "bar");

    // when
    authorizationState.createOrAddPermissions(ownerKey, resourceType, permissionType, resourceIds);

    // then
    final var resourceIdentifiers =
        authorizationState
            .getResourceIdentifiers(ownerKey, resourceType, permissionType)
            .getResourceIdentifiers();
    assertThat(resourceIdentifiers).containsExactly("foo", "bar");
  }

  @Test
  void shouldUpdatePermissionsIfAlreadyExists() {
    // given
    final var ownerKey = 1L;
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    final var resourceIds = List.of("foo", "bar");
    authorizationState.createOrAddPermissions(ownerKey, resourceType, permissionType, resourceIds);

    // when
    authorizationState.createOrAddPermissions(
        ownerKey, resourceType, permissionType, List.of("baz"));

    // then
    final var resourceIdentifiers =
        authorizationState
            .getResourceIdentifiers(ownerKey, resourceType, permissionType)
            .getResourceIdentifiers();
    assertThat(resourceIdentifiers).containsExactly("foo", "bar", "baz");
  }

  @Test
  void shouldStorePermissionsByOwnerKey() {
    // given
    final var ownerKey1 = 1L;
    final var ownerKey2 = 2L;
    final var resourceType = AuthorizationResourceType.DEPLOYMENT;
    final var permissionType = PermissionType.CREATE;
    authorizationState.createOrAddPermissions(
        ownerKey1, resourceType, permissionType, List.of("foo"));
    authorizationState.createOrAddPermissions(
        ownerKey2, resourceType, permissionType, List.of("bar"));

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
    final var resourceType2 = AuthorizationResourceType.JOB;
    final var permissionType = PermissionType.CREATE;
    authorizationState.createOrAddPermissions(
        ownerKey, resourceType1, permissionType, List.of("foo"));
    authorizationState.createOrAddPermissions(
        ownerKey, resourceType2, permissionType, List.of("bar"));

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
    authorizationState.createOrAddPermissions(
        ownerKey, resourceType, permissionType1, List.of("foo"));
    authorizationState.createOrAddPermissions(
        ownerKey, resourceType, permissionType2, List.of("bar"));

    // when
    final var resourceIds1 =
        authorizationState.getResourceIdentifiers(ownerKey, resourceType, permissionType1);
    final var resourceIds2 =
        authorizationState.getResourceIdentifiers(ownerKey, resourceType, permissionType2);

    // then
    assertThat(resourceIds1).isNotEqualTo(resourceIds2);
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
}
