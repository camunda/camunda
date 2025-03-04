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
import io.camunda.zeebe.engine.state.batchoperation.BatchOperation.BatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import java.util.ArrayList;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbBatchOperationState implements MutableBatchOperationState {

  private static final String KEY = "BATCH_OPERATION";
  private final Logger LOGGER = LoggerFactory.getLogger(DbBatchOperationState.class);
  private final DbLong batchKey = new DbLong();
  private final ColumnFamily<DbLong, BatchOperation> columnFamily;

  public DbBatchOperationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    columnFamily = zeebeDb.createColumnFamily(ZbColumnFamilies.BATCH_OPERATION, transactionContext,
        batchKey, new BatchOperation());
  }

  @Override
  public void create(final long batchKey, final BatchOperationCreationRecord record) {
    this.batchKey.wrapLong(batchKey);
    final var batch = new BatchOperation();
    batch.setStatus(BatchOperationState.ACTIVATED);
    batch.setType(record.getBatchOperationType());
    batch.setOffset(0);
    batch.setKeys(new ArrayList<>(record.getKeys())); // todo record also as list?
    columnFamily.insert(this.batchKey, batch);
  }

  @Override
  public void update(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(batchKey);
    final var batch = columnFamily.get(this.batchKey);
    batch.setOffset(record.getOffset());
    columnFamily.insert(this.batchKey, batch);
  }

  @Override
  public void pause(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(batchKey);
    final var batch = columnFamily.get(this.batchKey);
    if (batch == null) {
      LOGGER.warn("Batch operation with key {} not found for pausing", batchKey);
      return;
    }
    batch.setStatus(BatchOperationState.PAUSED);
    columnFamily.insert(this.batchKey, batch);
  }

  @Override
  public void resume(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(batchKey);
    final var batch = columnFamily.get(this.batchKey);
    if (batch == null) {
      LOGGER.warn("Batch operation with key {} not found for resuming", batchKey);
      return;
    }
    batch.setStatus(BatchOperationState.ACTIVATED);
    columnFamily.insert(this.batchKey, batch);
  }

  @Override
  public void cancel(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(batchKey);
    columnFamily.deleteExisting(this.batchKey);
  }

  @Override
  public void complete(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(batchKey);
    columnFamily.deleteExisting(this.batchKey);
  }

  @Override
  public Optional<BatchOperation> get(final long key) {
    batchKey.wrapLong(key);
    return Optional.ofNullable(columnFamily.get(batchKey));
  }

}
