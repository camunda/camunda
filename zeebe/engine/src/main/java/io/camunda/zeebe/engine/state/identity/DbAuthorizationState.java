/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.identity;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.identity.AuthorizationRecord;

public class DbAuthorizationState implements AuthorizationState, MutableAuthorizationState {
  private final AuthorizationRecordValue authorizationRecordToRead = new AuthorizationRecordValue();
  private final AuthorizationRecordValue authorizationRecordToWrite =
      new AuthorizationRecordValue();

  private final PermissionsRecordValue permissionsRecordToRead = new PermissionsRecordValue();
  private final PermissionsRecordValue permissionsRecordToWrite = new PermissionsRecordValue();

  private final DbLong authorizationKey;
  private final DbString ownerKey;
  private final DbString ownerType;
  private final DbString resourceKey;
  private final DbString resourceType;
  private final DbCompositeKey<DbString, DbString> resourceCompositeKey;
  private final DbCompositeKey<
          DbCompositeKey<DbString, DbString>, DbCompositeKey<DbString, DbString>>
      ownerAndResourceCompositeKey;
  private final ColumnFamily<DbLong, AuthorizationRecordValue> authorizationColumnFamily;
  private final ColumnFamily<
          DbCompositeKey<DbCompositeKey<DbString, DbString>, DbCompositeKey<DbString, DbString>>,
          PermissionsRecordValue>
      ownerAuthorizationColumnFamily;

  public DbAuthorizationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    authorizationKey = new DbLong();
    authorizationColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AUTHORIZATIONS,
            transactionContext,
            authorizationKey,
            authorizationRecordToRead);

    ownerKey = new DbString();
    ownerType = new DbString();
    resourceKey = new DbString();
    resourceType = new DbString();
    resourceCompositeKey = new DbCompositeKey<>(resourceKey, resourceType);
    ownerAndResourceCompositeKey =
        new DbCompositeKey<>(new DbCompositeKey<>(ownerKey, ownerType), resourceCompositeKey);

    ownerAuthorizationColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.AUTHORIZATIONS_BY_USERNAME_AND_PERMISSION,
            transactionContext,
            ownerAndResourceCompositeKey,
            permissionsRecordToRead);
  }

  @Override
  public void createAuthorization(final AuthorizationRecord authorizationRecord) {
    authorizationKey.wrapLong(authorizationRecord.getAuthorizationKey());
    authorizationRecordToWrite.setRecord(authorizationRecord);
    authorizationColumnFamily.insert(authorizationKey, authorizationRecordToWrite);

    ownerKey.wrapString(authorizationRecord.getOwnerKey());
    ownerType.wrapString(authorizationRecord.getOwnerType());
    resourceKey.wrapString(authorizationRecord.getResourceKey());
    resourceType.wrapString(authorizationRecord.getResourceType());
    permissionsRecordToWrite.setPermissions(authorizationRecord.getPermissions());
    ownerAuthorizationColumnFamily.insert(ownerAndResourceCompositeKey, permissionsRecordToWrite);
  }

  @Override
  public void updateAuthorization(final AuthorizationRecord authorizationRecord) {
    authorizationKey.wrapLong(authorizationRecord.getAuthorizationKey());
    authorizationRecordToWrite.setRecord(authorizationRecord);
    authorizationColumnFamily.update(authorizationKey, authorizationRecordToWrite);
  }

  @Override
  public void deleteAuthorization(final Long key) {
    authorizationKey.wrapLong(key);
    authorizationColumnFamily.deleteExisting(authorizationKey);
  }

  @Override
  public AuthorizationRecord getAuthorization(final Long key) {
    authorizationKey.wrapLong(key);
    final AuthorizationRecordValue authorizationRecordValue =
        authorizationColumnFamily.get(authorizationKey);
    return authorizationRecordValue == null ? null : authorizationRecordToRead.getRecord();
  }

  @Override
  public PermissionsRecordValue getAuthorization(
      final String ownerKey,
      final String ownerType,
      final String resourceKey,
      final String resourceType) {
    this.ownerKey.wrapString(ownerKey);
    this.ownerType.wrapString(ownerType);
    this.resourceKey.wrapString(resourceKey);
    this.resourceType.wrapString(resourceType);
    return ownerAuthorizationColumnFamily.get(ownerAndResourceCompositeKey);
  }
}
