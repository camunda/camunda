/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.batchoperation;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.authorization.PersistedAuthorization;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Optional;
import java.util.Set;

public class DbBatchOperationState implements MutableBatchOperationState {
  private static final String KEY = "BATCH_OPERATION";

  private final DbLong batchKey = new DbLong();
  private final ColumnFamily<DbLong, ItemKeys> columnFamily;

  public DbBatchOperationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    columnFamily = zeebeDb.createColumnFamily(ZbColumnFamilies.BATCH_OPERATION, transactionContext,
        batchKey, new ItemKeys());
  }

  @Override
  public void create(final long batchKey, final BatchOperationCreationRecord record) {
    this.batchKey.wrapLong(batchKey);
    final var itemKeys = new ItemKeys();
    itemKeys.setKeys(record.getKeys());
    columnFamily.insert(this.batchKey, itemKeys);
  }

  @Override
  public Optional<PersistedAuthorization> get(final long key) {
    batchKey.wrapLong(key);
    return null;
  }

}
