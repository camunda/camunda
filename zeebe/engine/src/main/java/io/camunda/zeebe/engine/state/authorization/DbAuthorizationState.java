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
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
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

  // authorization key -> owner type + owner id + resource type
  private final DbForeignKey<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>>
      fkPermissionKey;
  private final ColumnFamily<
          DbLong, DbForeignKey<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>>>
      permissionKeyByAuthorizationKeyColumnFamily;

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

    fkPermissionKey =
        new DbForeignKey<>(ownerTypeOwnerIdAndResourceType, ZbColumnFamilies.PERMISSIONS);
    permissionKeyByAuthorizationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PERMISSION_KEY_BY_AUTHORIZATION_KEY,
            transactionContext,
            authorizationKey,
            fkPermissionKey);
  }

  @Override
  public void createOrAddPermission(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final Set<String> resourceIds) {
    this.ownerType.wrapString(ownerType.name());
    this.ownerId.wrapString(ownerId);
    this.resourceType.wrapString(resourceType.name());

    final var identifiers =
        Optional.ofNullable(permissionsColumnFamily.get(ownerTypeOwnerIdAndResourceType))
            .orElse(new Permissions());

    identifiers.addResourceIdentifiers(permissionType, resourceIds);
    permissionsColumnFamily.upsert(ownerTypeOwnerIdAndResourceType, identifiers);
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
        .getAuthorizationPermissions()
        .forEach(
            permissionType -> {
              permissions.addResourceIdentifier(permissionType, authorization.getResourceId());
            });
    permissionsColumnFamily.upsert(ownerTypeOwnerIdAndResourceType, permissions);

    // add authorization key and permission key record
    permissionKeyByAuthorizationKeyColumnFamily.insert(this.authorizationKey, fkPermissionKey);
  }

  @Override
  public void removePermission(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final Set<String> resourceIds) {
    this.ownerType.wrapString(ownerType.name());
    this.ownerId.wrapString(ownerId);
    this.resourceType.wrapString(resourceType.name());

    final var permissions = permissionsColumnFamily.get(ownerTypeOwnerIdAndResourceType);
    permissions.removeResourceIdentifiers(permissionType, resourceIds);

    if (permissions.isEmpty()) {
      permissionsColumnFamily.deleteExisting(ownerTypeOwnerIdAndResourceType);
    } else {
      permissionsColumnFamily.update(ownerTypeOwnerIdAndResourceType, permissions);
    }
  }

  @Override
  public void deleteAuthorizationsByOwnerTypeAndIdPrefix(
      final AuthorizationOwnerType ownerType, final String ownerId) {
    this.ownerType.wrapString(ownerType.name());
    this.ownerId.wrapString(ownerId);
    permissionsColumnFamily.whileEqualPrefix(
        ownerTypeAndOwnerId,
        (compositeKey, permissions) -> {
          permissionsColumnFamily.deleteExisting(compositeKey);
        });
  }

  @Override
  public void delete(final long authorizationKey) {
    this.authorizationKey.wrapLong(authorizationKey);
    final var persistedAuthorization =
        authorizationByAuthorizationKeyColumnFamily.get(this.authorizationKey);

    // remove the old permissions
    persistedAuthorization
        .getPermissions()
        .forEach(
            permissionType -> {
              removePermission(
                  persistedAuthorization.getOwnerType(),
                  persistedAuthorization.getOwnerId(),
                  persistedAuthorization.getResourceType(),
                  permissionType,
                  Set.of(persistedAuthorization.getResourceId()));
            });

    // delete the old authorization record
    authorizationByAuthorizationKeyColumnFamily.deleteExisting(this.authorizationKey);
  }

  @Override
  public Optional<PersistedAuthorization> get(final long authorizationKey) {
    this.authorizationKey.wrapLong(authorizationKey);
    final var persistedAuthorization =
        authorizationByAuthorizationKeyColumnFamily.get(this.authorizationKey);
    return Optional.ofNullable(persistedAuthorization);
  }

  @Override
  public Set<String> getResourceIdentifiers(
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
}
