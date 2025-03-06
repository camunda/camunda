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
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationSubbatchRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbBatchOperationState implements MutableBatchOperationState {

  private static final String KEY = "BATCH_OPERATION";
  private final Logger LOGGER = LoggerFactory.getLogger(DbBatchOperationState.class);
  private final DbLong batchKey = new DbLong();
  private final ColumnFamily<DbLong, PersistedBatchOperation> batchOperationColumnFamily;
  private final ColumnFamily<DbLong, DbNil> pendingBatchOperationColumnFamily;

  public DbBatchOperationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    batchOperationColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BATCH_OPERATION,
            transactionContext,
            batchKey,
            new PersistedBatchOperation());
    pendingBatchOperationColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PENDING_BATCH_OPERATION, transactionContext, batchKey, DbNil.INSTANCE);
  }

  @Override
  public void create(final long batchKey, final BatchOperationCreationRecord record) {
    LOGGER.debug("Creating batch operation with key {}", record.getBatchOperationKey());
    this.batchKey.wrapLong(record.getBatchOperationKey());
    final var batchOperation = new PersistedBatchOperation();
    batchOperation
        .setKey(record.getBatchOperationKey())
        .setStatus(BatchOperationState.ACTIVATED)
        .setBatchOperationType(record.getBatchOperationType())
        .setIntent(BatchOperationIntent.CREATED)
        .setFilter(record.getFilterBuffer())
        .setOffset(0);
    batchOperationColumnFamily.insert(this.batchKey, batchOperation);
    pendingBatchOperationColumnFamily.upsert(this.batchKey, DbNil.INSTANCE);
  }

  @Override
  public void updateOffset(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(record.getBatchOperationKey());
    final var batch = batchOperationColumnFamily.get(this.batchKey);
    if (batch == null) {
      LOGGER.warn("Batch operation with key {} not found for updating",
          record.getBatchOperationKey());
      return;
    }
    batch.setOffset(record.getOffset());
    batchOperationColumnFamily.update(this.batchKey, batch);
    pendingBatchOperationColumnFamily.deleteIfExists(this.batchKey);
  }

  @Override
  public void appendKeys(final long batchKey, final BatchOperationSubbatchRecord record) {
    final var batch = batchOperationColumnFamily.get(this.batchKey);
    if (batch == null) {
      LOGGER.warn("Batch operation with key {} not found for appending keys",
          record.getBatchOperationKey());
      return;
    }
    batch.appendKeys(record.getKeys());
    batchOperationColumnFamily.update(this.batchKey, batch);
  }

  @Override
  public void pause(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(record.getBatchOperationKey());
    final var batch = batchOperationColumnFamily.get(this.batchKey);
    if (batch == null) {
      LOGGER.warn("Batch operation with key {} not found for pausing",
          record.getBatchOperationKey());
      return;
    }
    batch.setStatus(BatchOperationState.PAUSED);
    batchOperationColumnFamily.insert(this.batchKey, batch);
  }

  @Override
  public void resume(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(record.getBatchOperationKey());
    final var batch = batchOperationColumnFamily.get(this.batchKey);
    if (batch == null) {
      LOGGER.warn("Batch operation with key {} not found for resuming",
          record.getBatchOperationKey());
      return;
    }
    batch.setStatus(BatchOperationState.ACTIVATED);
    batchOperationColumnFamily.insert(this.batchKey, batch);
  }

  @Override
  public void cancel(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(record.getBatchOperationKey());
    batchOperationColumnFamily.deleteExisting(this.batchKey);
  }

  @Override
  public void complete(final long batchKey, final BatchOperationExecutionRecord record) {
    LOGGER.debug("Completing batch operation with key {}", record.getBatchOperationKey());

    this.batchKey.wrapLong(record.getBatchOperationKey());
    batchOperationColumnFamily.deleteExisting(this.batchKey);
  }

  @Override
  public Optional<PersistedBatchOperation> get(final long key) {
    batchKey.wrapLong(key);
    return Optional.ofNullable(batchOperationColumnFamily.get(batchKey));
  }

  @Override
  public void foreachPendingBatchOperation(final BatchOperationVisitor visitor) {
    pendingBatchOperationColumnFamily.whileTrue(
        (key, nil) -> {
          final var batchOperation = batchOperationColumnFamily.get(key);
          if (batchOperation != null) {
            visitor.visit(batchOperation);
          }
          return true;
        });
  }
}
