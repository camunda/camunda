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
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class DbAuthorizationState implements MutableAuthorizationState {

  private final PersistedAuthorization persistedAuthorization = new PersistedAuthorization();

  private final DbString ownerType;
  private final DbString ownerId;
  private final DbString resourceType;
  private final DbCompositeKey<DbString, DbString> ownerTypeAndOwnerId;
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>
      ownerTypeOwnerIdAndResourceType;
  // owner type + owner id + resource type -> permissions
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>, Permissions>
      permissionsColumnFamily;

  // authorization key -> authorization
  private final DbLong authorizationKey;
  private final ColumnFamily<DbLong, PersistedAuthorization>
      authorizationByAuthorizationKeyColumnFamily;

  // ownerType + ownerId -> authorizationKeys
  private final ColumnFamily<DbCompositeKey<DbString, DbString>, AuthorizationKeys>
      authorizationKeysByOwnerColumnFamily;

  public DbAuthorizationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    ownerType = new DbString();
    ownerId = new DbString();
    resourceType = new DbString();
    ownerTypeAndOwnerId = new DbCompositeKey<>(ownerType, ownerId);
    ownerTypeOwnerIdAndResourceType = new DbCompositeKey<>(ownerTypeAndOwnerId, resourceType);
    permissionsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PERMISSIONS,
            transactionContext,
            ownerTypeOwnerIdAndResourceType,
            new Permissions());

    authorizationKey = new DbLong();
    authorizationByAuthorizationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AUTHORIZATIONS,
            transactionContext,
            authorizationKey,
            new PersistedAuthorization());

    authorizationKeysByOwnerColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AUTHORIZATION_KEYS_BY_OWNER,
            transactionContext,
            ownerTypeAndOwnerId,
            new AuthorizationKeys());
  }

  @Override
  public void create(final long authorizationKey, final AuthorizationRecord authorization) {
    this.authorizationKey.wrapLong(authorizationKey);
    persistedAuthorization.wrap(authorization);
    authorizationByAuthorizationKeyColumnFamily.insert(
        this.authorizationKey, persistedAuthorization);

    ownerId.wrapString(authorization.getOwnerId());
    ownerType.wrapString(authorization.getOwnerType().name());
    resourceType.wrapString(authorization.getResourceType().name());

    final var permissions =
        Optional.ofNullable(permissionsColumnFamily.get(ownerTypeOwnerIdAndResourceType))
            .orElse(new Permissions());

    authorization
        .getPermissionTypes()
        .forEach(
            permissionType -> {
              permissions.addAuthorizationScope(
                  permissionType,
                  new AuthorizationScope(
                      authorization.getResourceMatcher(), authorization.getResourceId()));
            });
    permissionsColumnFamily.upsert(ownerTypeOwnerIdAndResourceType, permissions);

    // add authorization key to owner
    final var keys =
        Optional.ofNullable(authorizationKeysByOwnerColumnFamily.get(ownerTypeAndOwnerId))
            .orElse(new AuthorizationKeys());
    keys.addAuthorizationKey(authorizationKey);
    authorizationKeysByOwnerColumnFamily.upsert(ownerTypeAndOwnerId, keys);
  }

  @Override
  public void update(final long authorizationKey, final AuthorizationRecord authorization) {
    // delete old authorization record
    delete(authorizationKey);

    // create the new authorization record
    create(authorizationKey, authorization);
  }

  @Override
  public void delete(final long authorizationKey) {
    this.authorizationKey.wrapLong(authorizationKey);
    final var persistedAuthorization =
        authorizationByAuthorizationKeyColumnFamily.get(this.authorizationKey);

    // remove the old permissions
    persistedAuthorization
        .getPermissionTypes()
        .forEach(
            permissionType -> {
              removePermission(
                  persistedAuthorization.getOwnerType(),
                  persistedAuthorization.getOwnerId(),
                  persistedAuthorization.getResourceType(),
                  permissionType,
                  Set.of(
                      new AuthorizationScope(
                          persistedAuthorization.getResourceMatcher(),
                          persistedAuthorization.getResourceId())));
            });

    // delete the old authorization record
    authorizationByAuthorizationKeyColumnFamily.deleteExisting(this.authorizationKey);

    // remove authorization key from owner
    ownerId.wrapString(persistedAuthorization.getOwnerId());
    ownerType.wrapString(persistedAuthorization.getOwnerType().name());
    final var keys = authorizationKeysByOwnerColumnFamily.get(ownerTypeAndOwnerId);
    keys.removeAuthorizationKey(authorizationKey);
    authorizationKeysByOwnerColumnFamily.update(ownerTypeAndOwnerId, keys);
  }

  @Override
  public Optional<PersistedAuthorization> get(final long authorizationKey) {
    this.authorizationKey.wrapLong(authorizationKey);
    final var persistedAuthorization =
        authorizationByAuthorizationKeyColumnFamily.get(this.authorizationKey);
    return Optional.ofNullable(persistedAuthorization);
  }

  @Override
  public Set<AuthorizationScope> getAuthorizationScopes(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    this.ownerType.wrapString(ownerType.name());
    this.ownerId.wrapString(ownerId);
    this.resourceType.wrapString(resourceType.name());

    final var persistedPermissions = permissionsColumnFamily.get(ownerTypeOwnerIdAndResourceType);

    return persistedPermissions == null
        ? Collections.emptySet()
        : persistedPermissions
            .getPermissions()
            .getOrDefault(permissionType, Collections.emptySet());
  }

  @Override
  public Set<Long> getAuthorizationKeysForOwner(
      final AuthorizationOwnerType ownerType, final String ownerId) {
    this.ownerType.wrapString(ownerType.name());
    this.ownerId.wrapString(ownerId);
    final var keys = authorizationKeysByOwnerColumnFamily.get(ownerTypeAndOwnerId);
    return keys == null ? Collections.emptySet() : keys.getAuthorizationKeys();
  }

  private void removePermission(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final Set<AuthorizationScope> resourceIds) {
    this.ownerType.wrapString(ownerType.name());
    this.ownerId.wrapString(ownerId);
    this.resourceType.wrapString(resourceType.name());

    final var permissions = permissionsColumnFamily.get(ownerTypeOwnerIdAndResourceType);
    permissions.removeAuthorizationScopes(permissionType, resourceIds);

    if (permissions.isEmpty()) {
      permissionsColumnFamily.deleteExisting(ownerTypeOwnerIdAndResourceType);
    } else {
      permissionsColumnFamily.update(ownerTypeOwnerIdAndResourceType, permissions);
    }
  }
}
