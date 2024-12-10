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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DbAuthorizationState implements AuthorizationState, MutableAuthorizationState {

  private final Permissions permissions = new Permissions();

  private final DbLong ownerKey;
  private final DbString resourceType;
  private final DbCompositeKey<DbLong, DbString> ownerKeyAndResourceType;
  // owner key + resource type -> permissions
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, Permissions> permissionsColumnFamily;

  private final DbString resourceId;
  private final DbCompositeKey<DbString, DbCompositeKey<DbLong, DbString>>
      resourceIdAndOwnerKeyAndResourceType;
  // resource id + owner key + resource type -> DbNil
  private final ColumnFamily<DbCompositeKey<DbString, DbCompositeKey<DbLong, DbString>>, DbNil>
      authorizationKeyByResourceIdColumnFamily;

  private final DbString ownerType;
  // owner key -> owner type
  private final ColumnFamily<DbLong, DbString> ownerTypeByOwnerKeyColumnFamily;

  public DbAuthorizationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    ownerKey = new DbLong();
    resourceType = new DbString();
    ownerKeyAndResourceType = new DbCompositeKey<>(ownerKey, resourceType);

    permissionsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PERMISSIONS, transactionContext, ownerKeyAndResourceType, permissions);

    resourceId = new DbString();
    resourceIdAndOwnerKeyAndResourceType =
        new DbCompositeKey<>(resourceId, ownerKeyAndResourceType);
    authorizationKeyByResourceIdColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AUTHORIZATION_KEY_BY_RESOURCE_ID,
            transactionContext,
            resourceIdAndOwnerKeyAndResourceType,
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
      final Set<String> resourceIds) {
    this.ownerKey.wrapLong(ownerKey);
    this.resourceType.wrapString(resourceType.name());

    final var identifiers =
        Optional.ofNullable(permissionsColumnFamily.get(ownerKeyAndResourceType))
            .orElse(new Permissions());

    identifiers.addResourceIdentifiers(permissionType, resourceIds);
    permissionsColumnFamily.upsert(ownerKeyAndResourceType, identifiers);

    resourceIds.forEach(
        resourceId -> {
          this.resourceId.wrapString(resourceId);
          authorizationKeyByResourceIdColumnFamily.upsert(
              resourceIdAndOwnerKeyAndResourceType, DbNil.INSTANCE);
        });
  }

  @Override
  public void removePermission(
      final long ownerKey,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final Set<String> resourceIds) {
    this.ownerKey.wrapLong(ownerKey);
    this.resourceType.wrapString(resourceType.name());

    final var permissions = permissionsColumnFamily.get(ownerKeyAndResourceType);
    resourceIds.forEach(
        resourceId -> {
          this.resourceId.wrapString(resourceId);
          authorizationKeyByResourceIdColumnFamily.deleteExisting(
              resourceIdAndOwnerKeyAndResourceType);
        });

    permissions.removeResourceIdentifiers(permissionType, resourceIds);

    if (permissions.isEmpty()) {
      permissionsColumnFamily.deleteExisting(ownerKeyAndResourceType);
    } else {
      permissionsColumnFamily.update(ownerKeyAndResourceType, permissions);
    }
  }

  @Override
  public void insertOwnerTypeByKey(final long ownerKey, final AuthorizationOwnerType ownerType) {
    this.ownerKey.wrapLong(ownerKey);
    this.ownerType.wrapString(ownerType.name());
    ownerTypeByOwnerKeyColumnFamily.insert(this.ownerKey, this.ownerType);
  }

  @Override
  public void deleteAuthorizationsByOwnerKeyPrefix(final long ownerKey) {
    this.ownerKey.wrapLong(ownerKey);
    permissionsColumnFamily.whileEqualPrefix(
        this.ownerKey,
        (compositeKey, permissions) -> {
          resourceType.wrapString(compositeKey.second().toString());

          permissions.getPermissions().values().stream()
              .flatMap(Set::stream)
              .distinct()
              .forEach(
                  resourceId -> {
                    this.resourceId.wrapString(resourceId);
                    authorizationKeyByResourceIdColumnFamily.deleteExisting(
                        resourceIdAndOwnerKeyAndResourceType);
                  });

          permissionsColumnFamily.deleteExisting(compositeKey);
        });
  }

  @Override
  public void deleteOwnerTypeByKey(final long ownerKey) {
    this.ownerKey.wrapLong(ownerKey);
    ownerTypeByOwnerKeyColumnFamily.deleteExisting(this.ownerKey);
  }

  @Override
  public Set<String> getResourceIdentifiers(
      final Long ownerKey,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    this.ownerKey.wrapLong(ownerKey);
    this.resourceType.wrapString(resourceType.name());

    final var persistedPermissions = permissionsColumnFamily.get(ownerKeyAndResourceType);

    return persistedPermissions == null
        ? Collections.emptySet()
        : persistedPermissions
            .getPermissions()
            .getOrDefault(permissionType, Collections.emptySet());
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

  @Override
  public List<AuthorizationKey> getAuthorizationKeysByResourceId(final String resourceId) {
    final var authorizationKeys = new ArrayList<AuthorizationKey>();
    this.resourceId.wrapString(resourceId);
    authorizationKeyByResourceIdColumnFamily.whileEqualPrefix(
        this.resourceId,
        (key, value) -> {
          final var ownerKey = key.second().first().getValue();
          final var resourceType = key.second().second().toString();
          authorizationKeys.add(new AuthorizationKey(ownerKey, resourceType));
        });
    return authorizationKeys;
  }
}
