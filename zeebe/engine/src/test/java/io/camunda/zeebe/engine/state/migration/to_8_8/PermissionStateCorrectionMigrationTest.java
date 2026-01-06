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
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.authorization.DbAuthorizationState;
import io.camunda.zeebe.engine.state.authorization.Permissions;
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
  }

  @Test
  void shouldMigrateWhenPermissionTypeIsMissing() {
    // given
    final var authorization1 =
        createAuthorization(
            1L,
            "process-definition-1",
            Set.of(PermissionType.CREATE_PROCESS_INSTANCE, PermissionType.DELETE_PROCESS_INSTANCE));

    // verify permissionState is complete
    ownerType.wrapString(authorization1.getOwnerType().name());
    ownerId.wrapString(authorization1.getOwnerId());
    resourceType.wrapString(authorization1.getResourceType().name());

    final var existingPermissionsForOwner =
        permissionsColumnFamily.get(ownerTypeOwnerIdAndResourceType, Permissions::new);

    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.CREATE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(authorization1.getResourceId()));
    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(authorization1.getResourceId()));

    // here we want to put the permissions CF into a state where one permission is missing
    // to allow the migration to add it back
    existingPermissionsForOwner.removeAuthorizationScopes(
        PermissionType.CREATE_PROCESS_INSTANCE,
        Set.of(AuthorizationScope.of(authorization1.getResourceId())));

    permissionsColumnFamily.upsert(ownerTypeOwnerIdAndResourceType, existingPermissionsForOwner);

    assertThat(existingPermissionsForOwner.getPermissions())
        .doesNotContainKey(PermissionType.CREATE_PROCESS_INSTANCE);
    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(authorization1.getResourceId()));

    // verify that after the migration is run, the missing permission is added back
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    assertThat(sut.needsToRun(context)).isTrue();
    sut.runMigration(context);

    final var permissionsAfterMigration =
        permissionsColumnFamily.get(ownerTypeOwnerIdAndResourceType, Permissions::new);
    assertThat(
            permissionsAfterMigration.getPermissions().get(PermissionType.CREATE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(authorization1.getResourceId()));
    assertThat(
            permissionsAfterMigration.getPermissions().get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(authorization1.getResourceId()));
  }

  @Test
  void shouldMigrateWhenAuthorizationScopeIsMissing() {
    // given
    final var authorizationRecord1 =
        createAuthorization(
            1L,
            "process-definition-1",
            Set.of(PermissionType.CREATE_PROCESS_INSTANCE, PermissionType.DELETE_PROCESS_INSTANCE));

    final var authorizationRecord2 =
        createAuthorization(
            2L, "process-definition-2", Set.of(PermissionType.CREATE_PROCESS_INSTANCE));

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
            AuthorizationScope.of(authorizationRecord1.getResourceId()),
            AuthorizationScope.of(authorizationRecord2.getResourceId()));
    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(authorizationRecord1.getResourceId()));

    // here we want to put the permissions CF into a state where one permission is missing
    // to allow the migration to add it back
    existingPermissionsForOwner.removeAuthorizationScopes(
        PermissionType.CREATE_PROCESS_INSTANCE,
        Set.of(AuthorizationScope.of(authorizationRecord1.getResourceId())));

    permissionsColumnFamily.upsert(ownerTypeOwnerIdAndResourceType, existingPermissionsForOwner);

    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.CREATE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(authorizationRecord2.getResourceId()));
    assertThat(
            existingPermissionsForOwner
                .getPermissions()
                .get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(authorizationRecord1.getResourceId()));

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
            AuthorizationScope.of(authorizationRecord1.getResourceId()),
            AuthorizationScope.of(authorizationRecord2.getResourceId()));
    assertThat(
            permissionsAfterMigration.getPermissions().get(PermissionType.DELETE_PROCESS_INSTANCE))
        .isNotEmpty()
        .containsExactly(AuthorizationScope.of(authorizationRecord1.getResourceId()));
  }

  private AuthorizationRecord createAuthorization(
      final long authorizationKey,
      final String resourceId,
      final Set<PermissionType> permissionTypes) {
    final var authorizationRecord =
        generateAuthorizationRecord(authorizationKey, resourceId, permissionTypes);
    state.create(authorizationKey, authorizationRecord);
    return authorizationRecord;
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
