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
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DbAuthorizationState implements AuthorizationState, MutableAuthorizationState {
  private final PersistedAuthorization persistedAuthorization = new PersistedAuthorization();

  private final ResourceIdentifiers resourceIdentifiers = new ResourceIdentifiers();

  private final DbLong ownerKey;
  private final DbString resourceType;
  private final DbString permissionType;
  private final DbCompositeKey<DbString, DbString> resourceTypeAndPermissionCompositeKey;
  private final DbCompositeKey<DbLong, DbCompositeKey<DbString, DbString>>
      ownerKeyAndResourceTypeAndPermissionCompositeKey;
  // owner key + resource type + permission -> resourceIds
  private final ColumnFamily<
          DbCompositeKey<DbLong, DbCompositeKey<DbString, DbString>>, ResourceIdentifiers>
      resourceIdsByOwnerKeyResourceTypeAndPermissionColumnFamily;

  private final DbString resourceId;
  private final DbCompositeKey<DbString, DbCompositeKey<DbLong, DbCompositeKey<DbString, DbString>>>
      resourceIdAndOwnerKeyAndResourceTypeAndPermissionTypeCompositeKey;
  // resource id + owner key + resource type + permission type -> DbNil
  private final ColumnFamily<
          DbCompositeKey<DbString, DbCompositeKey<DbLong, DbCompositeKey<DbString, DbString>>>,
          DbNil>
      authorizationKeyByResourceIdColumnFamily;

  private final DbString ownerType;
  // owner key -> owner type
  private final ColumnFamily<DbLong, DbString> ownerTypeByOwnerKeyColumnFamily;

  public DbAuthorizationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    ownerKey = new DbLong();
    resourceType = new DbString();
    permissionType = new DbString();
    resourceTypeAndPermissionCompositeKey = new DbCompositeKey<>(resourceType, permissionType);
    ownerKeyAndResourceTypeAndPermissionCompositeKey =
        new DbCompositeKey<>(ownerKey, resourceTypeAndPermissionCompositeKey);

    resourceIdsByOwnerKeyResourceTypeAndPermissionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RESOURCE_IDS_BY_OWNER_KEY_RESOURCE_TYPE_AND_PERMISSION,
            transactionContext,
            ownerKeyAndResourceTypeAndPermissionCompositeKey,
            resourceIdentifiers);

    resourceId = new DbString();
    resourceIdAndOwnerKeyAndResourceTypeAndPermissionTypeCompositeKey =
        new DbCompositeKey<>(resourceId, ownerKeyAndResourceTypeAndPermissionCompositeKey);
    authorizationKeyByResourceIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AUTHORIZATION_KEY_BY_RESOURCE_ID,
            transactionContext,
            resourceIdAndOwnerKeyAndResourceTypeAndPermissionTypeCompositeKey,
            DbNil.INSTANCE);

    ownerType = new DbString();
    ownerTypeByOwnerKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.OWNER_TYPE_BY_OWNER_KEY, transactionContext, ownerKey, ownerType);
  }

  @Override
  public void createOrAddPermission(
      final long ownerKey,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final List<String> resourceIds) {
    this.ownerKey.wrapLong(ownerKey);
    this.resourceType.wrapString(resourceType.name());
    this.permissionType.wrapString(permissionType.name());

    final var identifiers =
        Optional.ofNullable(
                resourceIdsByOwnerKeyResourceTypeAndPermissionColumnFamily.get(
                    ownerKeyAndResourceTypeAndPermissionCompositeKey))
            .orElse(new ResourceIdentifiers());

    identifiers.addResourceIdentifiers(resourceIds);
    resourceIdsByOwnerKeyResourceTypeAndPermissionColumnFamily.upsert(
        ownerKeyAndResourceTypeAndPermissionCompositeKey, identifiers);

    resourceIds.forEach(
        resourceId -> {
          this.resourceId.wrapString(resourceId);
          authorizationKeyByResourceIdColumnFamily.insert(
              resourceIdAndOwnerKeyAndResourceTypeAndPermissionTypeCompositeKey, DbNil.INSTANCE);
        });
  }

  @Override
  public void insertOwnerTypeByKey(final long ownerKey, final AuthorizationOwnerType ownerType) {
    this.ownerKey.wrapLong(ownerKey);
    this.ownerType.wrapString(ownerType.name());
    ownerTypeByOwnerKeyColumnFamily.insert(this.ownerKey, this.ownerType);
  }

  @Override
  public Set<String> getResourceIdentifiers(
      final Long ownerKey,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    this.ownerKey.wrapLong(ownerKey);
    this.resourceType.wrapString(resourceType.name());
    this.permissionType.wrapString(permissionType.name());

    final var persistedPermissions =
        resourceIdsByOwnerKeyResourceTypeAndPermissionColumnFamily.get(
            ownerKeyAndResourceTypeAndPermissionCompositeKey);

    return persistedPermissions == null
        ? Collections.emptySet()
        : persistedPermissions.getResourceIdentifiers();
  }

  @Override
  public Optional<AuthorizationOwnerType> getOwnerType(final long ownerKey) {
    this.ownerKey.wrapLong(ownerKey);
    final var ownerType = ownerTypeByOwnerKeyColumnFamily.get(this.ownerKey);

    if (ownerType == null) {
      return Optional.empty();
    }

    return Optional.of(AuthorizationOwnerType.valueOf(ownerType.toString()));
  }
}
