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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Stores every version of the authorization state.
 *
 * <p>An authorization is keyed by (authorizationKey, policyVersion), and a deleted version is a
 * tombstone. The keys of an owner and the permissions of an owner per resource type are aggregates,
 * so each version stores a full snapshot of them. A write at a version copies the snapshot visible
 * at that version, changes it, and stores it at that version. A read takes the latest snapshot that
 * is visible, which is the larger of the current policy version and the reading command's version.
 */
public class DbAuthorizationState implements MutableAuthorizationState {

  private final PersistedAuthorization persistedAuthorization = new PersistedAuthorization();

  // authorization key + version -> authorization
  private final DbLong authorizationKey = new DbLong();
  private final DbLong authorizationVersion = new DbLong();
  private final DbCompositeKey<DbLong, DbLong> versionedAuthorizationKey =
      new DbCompositeKey<>(authorizationKey, authorizationVersion);
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, PersistedAuthorization>
      authorizationByAuthorizationKeyColumnFamily;

  // owner type + owner id + version -> authorization keys
  private final DbString keysOwnerType = new DbString();
  private final DbString keysOwnerId = new DbString();
  private final DbLong keysVersion = new DbLong();
  private final DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong> versionedOwner =
      new DbCompositeKey<>(new DbCompositeKey<>(keysOwnerType, keysOwnerId), keysVersion);
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbString, DbString>, DbLong>, AuthorizationKeys>
      authorizationKeysByOwnerColumnFamily;

  // owner type + owner id + resource type + version -> permissions
  private final DbString permissionsOwnerType = new DbString();
  private final DbString permissionsOwnerId = new DbString();
  private final DbString permissionsResourceType = new DbString();
  private final DbLong permissionsVersion = new DbLong();
  private final DbCompositeKey<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>, DbLong>
      versionedOwnerAndResourceType =
          new DbCompositeKey<>(
              new DbCompositeKey<>(
                  new DbCompositeKey<>(permissionsOwnerType, permissionsOwnerId),
                  permissionsResourceType),
              permissionsVersion);
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbCompositeKey<DbString, DbString>, DbString>, DbLong>,
          Permissions>
      permissionsColumnFamily;

  private final LongSupplier currentVersion;

  public DbAuthorizationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final LongSupplier currentVersion) {
    this.currentVersion = currentVersion;
    permissionsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PERMISSIONS,
            transactionContext,
            versionedOwnerAndResourceType,
            new Permissions());

    authorizationByAuthorizationKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AUTHORIZATIONS,
            transactionContext,
            versionedAuthorizationKey,
            new PersistedAuthorization());

    authorizationKeysByOwnerColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AUTHORIZATION_KEYS_BY_OWNER,
            transactionContext,
            versionedOwner,
            new AuthorizationKeys());
  }

  @Override
  public void create(final long authorizationKey, final AuthorizationRecord authorization) {
    final long version = authorization.getPolicyVersion();
    persistedAuthorization.wrap(authorization);
    persistedAuthorization.setDeleted(false);
    writeAuthorization(authorizationKey, version, persistedAuthorization);

    final var ownerType = authorization.getOwnerType().name();
    final var ownerId = authorization.getOwnerId();
    final var resourceType = authorization.getResourceType().name();
    final var permissions = permissionsAt(ownerType, ownerId, resourceType, version);
    authorization
        .getPermissionTypes()
        .forEach(
            permissionType ->
                permissions.addAuthorizationScope(
                    permissionType,
                    new AuthorizationScope(
                        authorization.getResourceMatcher(),
                        authorization.getResourceId(),
                        authorization.getResourcePropertyName())));
    writePermissions(ownerType, ownerId, resourceType, version, permissions);

    final var keys = keysAt(ownerType, ownerId, version);
    keys.addAuthorizationKey(authorizationKey);
    writeKeys(ownerType, ownerId, version, keys);
  }

  @Override
  public void update(final long authorizationKey, final AuthorizationRecord authorization) {
    delete(authorizationKey, authorization.getPolicyVersion());
    create(authorizationKey, authorization);
  }

  @Override
  public void delete(final long authorizationKey, final long policyVersion) {
    final var authorizationToDelete =
        authorizationAt(authorizationKey, policyVersion)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Expected to delete authorization with key '%d', but it does not exist"
                            .formatted(authorizationKey)));
    final var ownerType = authorizationToDelete.getOwnerType().name();
    final var ownerId = authorizationToDelete.getOwnerId();

    final var keys = keysAt(ownerType, ownerId, policyVersion);
    keys.removeAuthorizationKey(authorizationKey);

    // Permission types that another authorization of the same owner and scope still grants must
    // stay in the aggregate.
    final var sharedPermissionTypes =
        keys.getAuthorizationKeys().stream()
            .map(key -> authorizationAt(key, policyVersion).orElse(null))
            .filter(Objects::nonNull)
            .filter(stored -> matches(authorizationToDelete, stored))
            .flatMap(stored -> stored.getPermissionTypes().stream())
            .collect(Collectors.toSet());

    final var resourceType = authorizationToDelete.getResourceType().name();
    final var scope =
        new AuthorizationScope(
            authorizationToDelete.getResourceMatcher(),
            authorizationToDelete.getResourceId(),
            authorizationToDelete.getResourcePropertyName());
    for (final var permissionType : authorizationToDelete.getPermissionTypes()) {
      if (sharedPermissionTypes.contains(permissionType)) {
        continue;
      }
      final var permissions = permissionsAt(ownerType, ownerId, resourceType, policyVersion);
      permissions.removeAuthorizationScopes(permissionType, Set.of(scope));
      writePermissions(ownerType, ownerId, resourceType, policyVersion, permissions);
    }

    authorizationToDelete.setDeleted(true);
    writeAuthorization(authorizationKey, policyVersion, authorizationToDelete);
    writeKeys(ownerType, ownerId, policyVersion, keys);
  }

  @Override
  public Optional<PersistedAuthorization> get(
      final long authorizationKey, final long commandVersion) {
    return authorizationAt(authorizationKey, visibleVersion(commandVersion));
  }

  @Override
  public Set<AuthorizationScope> getAuthorizationScopes(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final long commandVersion) {
    final var permissions =
        permissionsAt(
            ownerType.name(), ownerId, resourceType.name(), visibleVersion(commandVersion));
    return permissions.getPermissions().getOrDefault(permissionType, Collections.emptySet());
  }

  @Override
  public Set<Long> getAuthorizationKeysForOwner(
      final AuthorizationOwnerType ownerType, final String ownerId, final long commandVersion) {
    return keysAt(ownerType.name(), ownerId, visibleVersion(commandVersion)).getAuthorizationKeys();
  }

  private Optional<PersistedAuthorization> authorizationAt(final long key, final long version) {
    authorizationKey.wrapLong(key);
    authorizationVersion.wrapLong(version);
    final var found = new PersistedAuthorization[1];
    authorizationByAuthorizationKeyColumnFamily.whileTrueReverse(
        versionedAuthorizationKey,
        (entryKey, value) -> {
          if (entryKey.first().getValue() != key) {
            return false;
          }
          if (entryKey.second().getValue() > version) {
            return true;
          }
          if (!value.isDeleted()) {
            found[0] = new PersistedAuthorization();
            found[0].copyFrom(value);
          }
          return false;
        });
    return Optional.ofNullable(found[0]);
  }

  private AuthorizationKeys keysAt(
      final String ownerType, final String ownerId, final long version) {
    keysOwnerType.wrapString(ownerType);
    keysOwnerId.wrapString(ownerId);
    keysVersion.wrapLong(version);
    final var found = new AuthorizationKeys();
    authorizationKeysByOwnerColumnFamily.whileTrueReverse(
        versionedOwner,
        (entryKey, value) -> {
          if (!entryKey.first().first().toString().equals(ownerType)
              || !entryKey.first().second().toString().equals(ownerId)) {
            return false;
          }
          if (entryKey.second().getValue() > version) {
            return true;
          }
          found.copyFrom(value);
          return false;
        });
    return found;
  }

  private Permissions permissionsAt(
      final String ownerType, final String ownerId, final String resourceType, final long version) {
    permissionsOwnerType.wrapString(ownerType);
    permissionsOwnerId.wrapString(ownerId);
    permissionsResourceType.wrapString(resourceType);
    permissionsVersion.wrapLong(version);
    final var found = new Permissions();
    permissionsColumnFamily.whileTrueReverse(
        versionedOwnerAndResourceType,
        (entryKey, value) -> {
          final var ownerAndResourceType = entryKey.first();
          if (!ownerAndResourceType.first().first().toString().equals(ownerType)
              || !ownerAndResourceType.first().second().toString().equals(ownerId)
              || !ownerAndResourceType.second().toString().equals(resourceType)) {
            return false;
          }
          if (entryKey.second().getValue() > version) {
            return true;
          }
          found.copyFrom(value);
          return false;
        });
    return found;
  }

  private void writeAuthorization(
      final long key, final long version, final PersistedAuthorization authorization) {
    authorizationKey.wrapLong(key);
    authorizationVersion.wrapLong(version);
    authorizationByAuthorizationKeyColumnFamily.upsert(versionedAuthorizationKey, authorization);
  }

  private void writeKeys(
      final String ownerType,
      final String ownerId,
      final long version,
      final AuthorizationKeys keys) {
    keysOwnerType.wrapString(ownerType);
    keysOwnerId.wrapString(ownerId);
    keysVersion.wrapLong(version);
    authorizationKeysByOwnerColumnFamily.upsert(versionedOwner, keys);
  }

  private void writePermissions(
      final String ownerType,
      final String ownerId,
      final String resourceType,
      final long version,
      final Permissions permissions) {
    permissionsOwnerType.wrapString(ownerType);
    permissionsOwnerId.wrapString(ownerId);
    permissionsResourceType.wrapString(resourceType);
    permissionsVersion.wrapLong(version);
    permissionsColumnFamily.upsert(versionedOwnerAndResourceType, permissions);
  }

  private long visibleVersion(final long commandVersion) {
    return Math.max(currentVersion.getAsLong(), commandVersion);
  }

  private static boolean matches(
      final PersistedAuthorization candidate, final PersistedAuthorization stored) {
    return candidate.getResourceType() == stored.getResourceType()
        && candidate.getResourceMatcher() == stored.getResourceMatcher()
        && Objects.equals(candidate.getResourceId(), stored.getResourceId())
        && Objects.equals(candidate.getResourcePropertyName(), stored.getResourcePropertyName());
  }
}
