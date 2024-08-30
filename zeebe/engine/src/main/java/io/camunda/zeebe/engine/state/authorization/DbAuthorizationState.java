/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;

public class DbAuthorizationState implements AuthorizationState, MutableAuthorizationState {
  private final PersistedAuthorization persistedAuthorization = new PersistedAuthorization();

  private final PersistedPermissions persistedPermissions = new PersistedPermissions();

  private final DbLong ownerKey;
  private final DbString ownerType;
  private final DbString resourceType;
  private final DbString permissionType;
  private final DbCompositeKey<DbString, DbString> resourceTypeAndPermissionTypeCompositeKey;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbString, DbString>>
      ownerKeyAndResourceTypeAndPermissionTypeCompositeKey;
  // owner key + resource type + permission type -> permissions
  private final ColumnFamily<
          DbCompositeKey<DbLong, DbCompositeKey<DbString, DbString>>, PersistedPermissions>
      permissionsByOwnerKeyResourceTypeAndPermissionTypeColumnFamily;

  public DbAuthorizationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    ownerKey = new DbLong();
    ownerType = new DbString();
    resourceType = new DbString();
    permissionType = new DbString();
    resourceTypeAndPermissionTypeCompositeKey = new DbCompositeKey<>(resourceType, permissionType);
    ownerKeyAndResourceTypeAndPermissionTypeCompositeKey =
        new DbCompositeKey<>(ownerKey, resourceTypeAndPermissionTypeCompositeKey);

    permissionsByOwnerKeyResourceTypeAndPermissionTypeColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PERMISSIONS_BY_OWNER_KEY_RESOURCE_TYPE_AND_PERMISSION_TYPE,
            transactionContext,
            ownerKeyAndResourceTypeAndPermissionTypeCompositeKey,
            persistedPermissions);
  }

  @Override
  public void createAuthorization(final AuthorizationRecord authorizationRecord) {
    persistedAuthorization.setAuthorization(authorizationRecord);

    ownerKey.wrapLong(authorizationRecord.getOwnerKey());
    ownerType.wrapString(authorizationRecord.getOwnerType().name());
    resourceType.wrapString(authorizationRecord.getResourceType());
    persistedPermissions.setPermissions(authorizationRecord.getPermissions());
    permissionsByOwnerKeyResourceTypeAndPermissionTypeColumnFamily.insert(
        ownerKeyAndResourceTypeAndPermissionTypeCompositeKey, persistedPermissions);
  }

  @Override
  public PersistedPermissions getPermissions(
      final Long ownerKey, final AuthorizationOwnerType ownerType, final String resourceType) {
    this.ownerKey.wrapLong(ownerKey);
    this.ownerType.wrapString(ownerType.name());
    this.resourceType.wrapString(resourceType);

    final var persistedPermissions =
        permissionsByOwnerKeyResourceTypeAndPermissionTypeColumnFamily.get(
            ownerKeyAndResourceTypeAndPermissionTypeCompositeKey);

    return persistedPermissions == null ? null : persistedPermissions.copy();
  }
}
