/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_8;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.authorization.Permissions;
import io.camunda.zeebe.engine.state.authorization.PersistedAuthorization;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;

public class DbPermissionMigrationState {
  private final DbString ownerType;
  private final DbString ownerId;
  private final DbString resourceType;
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>
      ownerTypeOwnerIdAndResourceType;
  // owner type + owner id + resource type -> permissions
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>, Permissions>
      permissionsColumnFamily;

  private final ColumnFamily<DbLong, PersistedAuthorization>
      authorizationByAuthorizationKeyColumnFamily;

  public DbPermissionMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
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

  public void migrateMissingPermissionsForAuthorizations() {
    authorizationByAuthorizationKeyColumnFamily.forEach(
        (key, authorization) ->
            authorization
                .getPermissionTypes()
                .forEach(
                    permissionType -> {
                      ownerType.wrapString(authorization.getOwnerType().name());
                      ownerId.wrapString(authorization.getOwnerId());
                      resourceType.wrapString(authorization.getResourceType().name());

                      final var existingPermissionsForOwner =
                          permissionsColumnFamily.get(
                              ownerTypeOwnerIdAndResourceType, Permissions::new);

                      if (existingPermissionsForOwner.getPermissions().get(permissionType)
                          == null) {
                        final var permissionsToStore =
                            buildPermissionsToStore(
                                existingPermissionsForOwner, authorization, permissionType);

                        permissionsColumnFamily.upsert(
                            ownerTypeOwnerIdAndResourceType, permissionsToStore);
                      } else if (!resourceMatchersContainAuthorizationScope(
                          authorization,
                          existingPermissionsForOwner.getPermissions().get(permissionType))) {

                        final var permissionsToStore =
                            buildPermissionsToStore(
                                existingPermissionsForOwner, authorization, permissionType);
                        permissionsColumnFamily.upsert(
                            ownerTypeOwnerIdAndResourceType, permissionsToStore);
                      }
                    }));
  }

  private boolean resourceMatchersContainAuthorizationScope(
      final PersistedAuthorization authorization,
      final Set<AuthorizationScope> authorizationScopes) {
    final var expectedAuthorizationScope =
        new AuthorizationScope(
            authorization.getResourceMatcher(),
            authorization.getResourceId(),
            authorization.getResourcePropertyName());

    return authorizationScopes.contains(expectedAuthorizationScope);
  }

  private Permissions buildPermissionsToStore(
      final Permissions existingPermissions,
      final PersistedAuthorization authorization,
      final PermissionType permissionType) {
    existingPermissions.addAuthorizationScope(
        permissionType,
        new AuthorizationScope(
            authorization.getResourceMatcher(),
            authorization.getResourceId(),
            authorization.getResourcePropertyName()));
    return existingPermissions;
  }
}
