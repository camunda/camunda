/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_8;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.authorization.DbAuthorizationState;
import io.camunda.zeebe.engine.state.authorization.Permissions;
import io.camunda.zeebe.engine.state.authorization.PersistedAuthorization;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContextImpl;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class PermissionStateCorrectionMigrationTest {
  final PermissionStateCorrectionMigration sut = new PermissionStateCorrectionMigration();

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private MutableProcessingState processingState;
  private TransactionContext transactionContext;

  private DbAuthorizationState state;

  private DbString ownerType;
  private DbString ownerId;
  private DbString resourceType;
  private DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>
      ownerTypeOwnerIdAndResourceType;
  // owner type + owner id + resource type -> permissions
  private ColumnFamily<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>, Permissions>
      permissionsColumnFamily;

  private ColumnFamily<DbLong, PersistedAuthorization> authorizationByAuthorizationKeyColumnFamily;

  @BeforeEach
  void setup() {
    state = new DbAuthorizationState(zeebeDb, transactionContext);

    ownerType = new DbString();
    ownerId = new DbString();
    resourceType = new DbString();
    final DbCompositeKey<DbString, DbString> ownerTypeAndOwnerId =
        new DbCompositeKey<>(ownerType, ownerId);
    ownerTypeOwnerIdAndResourceType = new DbCompositeKey<>(ownerTypeAndOwnerId, resourceType);
    permissionsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PERMISSIONS,
            transactionContext,
            ownerTypeOwnerIdAndResourceType,
            new Permissions());

    // authorization key -> authorization
    final DbLong authorizationKey = new DbLong();
    authorizationByAuthorizationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AUTHORIZATIONS,
            transactionContext,
            authorizationKey,
            new PersistedAuthorization());
  }

  @Test
  void shouldMigrateWhenPermissionTypeIsMissing() {
    // given
    final var authorizationKey = 1L;
    final var resourceId = "process-definition-1";
    final var permissionTypes =
        Set.of(PermissionType.CREATE_PROCESS_INSTANCE, PermissionType.DELETE_PROCESS_INSTANCE);
    final var authorizationRecord =
        generateAuthorizationRecord(authorizationKey, resourceId, permissionTypes);

    state.create(authorizationKey, authorizationRecord);

    // verify permissionState is complete
    ownerType.wrapString(authorizationRecord.getOwnerType().name());
    ownerId.wrapString(authorizationRecord.getOwnerId());
    resourceType.wrapString(authorizationRecord.getResourceType().name());

    final var existingPermissionsForOwner =
        permissionsColumnFamily.get(ownerTypeOwnerIdAndResourceType, Permissions::new);

    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.CREATE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(resourceId));
    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(resourceId));

    // here we want to put the permissions CF into a state where one permission is missing
    // to allow the migration to add it back
    existingPermissionsForOwner.removeAuthorizationScopes(
        PermissionType.CREATE_PROCESS_INSTANCE, Set.of(AuthorizationScope.of(resourceId)));

    permissionsColumnFamily.upsert(ownerTypeOwnerIdAndResourceType, existingPermissionsForOwner);

    assertThat(existingPermissionsForOwner.getPermissions())
        .doesNotContainKey(PermissionType.CREATE_PROCESS_INSTANCE);
    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(resourceId));

    // verify that after the migration is run, the missing permission is added back
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    assertThat(sut.needsToRun(context)).isTrue();
    sut.runMigration(context);

    final var permissionsAfterMigration =
        permissionsColumnFamily.get(ownerTypeOwnerIdAndResourceType, Permissions::new);
    assertThat(
            permissionsAfterMigration.getPermissions().get(PermissionType.CREATE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(resourceId));
    assertThat(
            permissionsAfterMigration.getPermissions().get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(resourceId));
  }

  @Test
  void shouldMigrateWhenAuthorizationScopeIsMissing() {
    // given
    final var authorizationKey1 = 1L;
    final var resourceId1 = "process-definition-1";
    final var permissionTypes1 =
        Set.of(PermissionType.CREATE_PROCESS_INSTANCE, PermissionType.DELETE_PROCESS_INSTANCE);
    final var authorizationRecord1 =
        generateAuthorizationRecord(authorizationKey1, resourceId1, permissionTypes1);

    state.create(authorizationKey1, authorizationRecord1);

    final var authorizationKey2 = 2L;
    final var resourceId2 = "process-definition-2";
    final var permissionTypes2 = Set.of(PermissionType.CREATE_PROCESS_INSTANCE);
    final var authorizationRecord2 =
        generateAuthorizationRecord(authorizationKey2, resourceId2, permissionTypes2);

    state.create(authorizationKey2, authorizationRecord2);

    // verify permissionState is complete
    ownerType.wrapString(authorizationRecord1.getOwnerType().name());
    ownerId.wrapString(authorizationRecord1.getOwnerId());
    resourceType.wrapString(authorizationRecord1.getResourceType().name());

    final var existingPermissionsForOwner =
        permissionsColumnFamily.get(ownerTypeOwnerIdAndResourceType, Permissions::new);

    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.CREATE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactlyInAnyOrder(
            AuthorizationScope.of(resourceId1), AuthorizationScope.of(resourceId2));
    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(resourceId1));

    // here we want to put the permissions CF into a state where one permission is missing
    // to allow the migration to add it back
    existingPermissionsForOwner.removeAuthorizationScopes(
        PermissionType.CREATE_PROCESS_INSTANCE, Set.of(AuthorizationScope.of(resourceId1)));

    permissionsColumnFamily.upsert(ownerTypeOwnerIdAndResourceType, existingPermissionsForOwner);

    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.CREATE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(resourceId2));
    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(resourceId1));

    // verify that after the migration is run, the missing permission is added back
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    assertThat(sut.needsToRun(context)).isTrue();
    sut.runMigration(context);

    final var permissionsAfterMigration =
        permissionsColumnFamily.get(ownerTypeOwnerIdAndResourceType, Permissions::new);
    assertThat(
            permissionsAfterMigration.getPermissions().get(PermissionType.CREATE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactlyInAnyOrder(
            AuthorizationScope.of(resourceId1), AuthorizationScope.of(resourceId2));
    assertThat(
            permissionsAfterMigration.getPermissions().get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(resourceId1));
  }

  private static AuthorizationRecord generateAuthorizationRecord(
      final long authorizationKey,
      final String resourceId,
      final Set<PermissionType> permissionTypes) {
    final var authorizationRecord = new AuthorizationRecord();
    authorizationRecord.setAuthorizationKey(authorizationKey);
    authorizationRecord.setOwnerType(AuthorizationOwnerType.USER);
    authorizationRecord.setOwnerId("user-1");
    authorizationRecord.setResourceType(AuthorizationResourceType.PROCESS_DEFINITION);
    authorizationRecord.setResourceId(resourceId);
    authorizationRecord.setResourceMatcher(AuthorizationResourceMatcher.ID);
    authorizationRecord.setPermissionTypes(permissionTypes);
    return authorizationRecord;
  }
}
