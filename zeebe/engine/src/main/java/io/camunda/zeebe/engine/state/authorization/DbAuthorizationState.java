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
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class DbAuthorizationState implements MutableAuthorizationState {

  private final PersistedAuthorization persistedAuthorization = new PersistedAuthorization();

  private final DbLong ownerKey;
  private final DbString resourceType;
  private final DbCompositeKey<DbLong, DbString> ownerKeyAndResourceType;
  // owner key + resource type -> permissions
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, Permissions> permissionsColumnFamily;

  private final DbString ownerType;
  // owner key -> owner type
  private final ColumnFamily<DbLong, DbString> ownerTypeByOwnerKeyColumnFamily;

  // authorization key -> authorization
  private final DbLong authorizationKey;
  private final ColumnFamily<DbLong, PersistedAuthorization>
      authorizationByAuthorizationKeyColumnFamily;

  public DbAuthorizationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    ownerKey = new DbLong();
    resourceType = new DbString();
    ownerKeyAndResourceType = new DbCompositeKey<>(ownerKey, resourceType);

    permissionsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PERMISSIONS,
            transactionContext,
            ownerKeyAndResourceType,
            new Permissions());

    ownerType = new DbString();
    ownerTypeByOwnerKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.OWNER_TYPE_BY_OWNER_KEY, transactionContext, ownerKey, ownerType);

    authorizationKey = new DbLong();
    authorizationByAuthorizationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AUTHORIZATIONS,
            transactionContext,
            authorizationKey,
            new PersistedAuthorization());
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
  }

  @Override
  public void create(final long authorizationKey, final AuthorizationRecord authorization) {
    this.authorizationKey.wrapLong(authorizationKey);
    persistedAuthorization.wrap(authorization);
    authorizationByAuthorizationKeyColumnFamily.insert(
        this.authorizationKey, persistedAuthorization);
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
          permissionsColumnFamily.deleteExisting(compositeKey);
        });
  }

  @Override
  public void deleteOwnerTypeByKey(final long ownerKey) {
    this.ownerKey.wrapLong(ownerKey);
    ownerTypeByOwnerKeyColumnFamily.deleteExisting(this.ownerKey);
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
}
