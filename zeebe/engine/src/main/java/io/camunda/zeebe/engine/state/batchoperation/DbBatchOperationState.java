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
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbBatchOperationState implements MutableBatchOperationState {

  public static final long MAX_BLOCK_SIZE = 3500;

  private static final Logger LOGGER = LoggerFactory.getLogger(DbBatchOperationState.class);

  private final DbLong batchKey = new DbLong();
  private final DbForeignKey<DbLong> fkBatchKey;
  private final DbLong blockKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbLong> fkBatchKeyAndBlockKey;

  private final ColumnFamily<DbLong, PersistedBatchOperation> batchOperationColumnFamily;
  private final ColumnFamily<
          DbCompositeKey<DbForeignKey<DbLong>, DbLong>, PersistedBatchOperationChunk>
      batchOperationEntitiesColumnFamily;
  private final ColumnFamily<DbLong, DbNil> pendingBatchOperationColumnFamily;

  public DbBatchOperationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    fkBatchKey = new DbForeignKey<>(batchKey, ZbColumnFamilies.BATCH_OPERATION);
    blockKey = new DbLong();
    fkBatchKeyAndBlockKey = new DbCompositeKey<>(fkBatchKey, blockKey);

    batchOperationColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BATCH_OPERATION,
            transactionContext,
            batchKey,
            new PersistedBatchOperation());
    batchOperationEntitiesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_BATCH_OPERATION,
            transactionContext,
            fkBatchKeyAndBlockKey,
            new PersistedBatchOperationChunk());
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
        .setStatus(BatchOperationStatus.CREATED)
        .setBatchOperationType(record.getBatchOperationType())
        .setEntityFilter(record.getEntityFilterBuffer());
    batchOperationColumnFamily.upsert(this.batchKey, batchOperation);
    pendingBatchOperationColumnFamily.upsert(this.batchKey, DbNil.INSTANCE);
  }

  @Override
  public void appendKeys(final long batchKey, final BatchOperationChunkRecord record) {
    LOGGER.trace(
        "Appending {} keys to batch operation with key {}",
        record.getEntityKeys().size(),
        record.getBatchOperationKey());
    final var batch = batchOperationColumnFamily.get(this.batchKey);

    pendingBatchOperationColumnFamily.deleteIfExists(this.batchKey);

    PersistedBatchOperationChunk block = null;
    for (final long key : record.getEntityKeys()) {
      final var currentBlockKey = batch.getMinBlockKey();
      if (currentBlockKey == -1) {
        block = createNewBlock(batch);
        LOGGER.trace(
            "Creating new block with key {} for batch operation with key {}",
            block.getKey(),
            record.getBatchOperationKey());
      } else if (block == null) {
        LOGGER.trace(
            "Loading block with key {} for batch operation with key {}",
            currentBlockKey,
            record.getBatchOperationKey());
        block = batchOperationEntitiesColumnFamily.get(fkBatchKeyAndBlockKey);
        blockKey.wrapLong(block.getKey());
      }
      if (block.getKeys().size() >= MAX_BLOCK_SIZE) {
        LOGGER.trace(
            "Block with key {} is full, inserting it and creating new block for batch operation with key {}",
            block.getKey(),
            record.getBatchOperationKey());
        batchOperationEntitiesColumnFamily.update(fkBatchKeyAndBlockKey, block);
        block = createNewBlock(batch);
      }
      block.appendKey(key);
    }
    if (block != null) {
      batchOperationEntitiesColumnFamily.update(fkBatchKeyAndBlockKey, block);
    }

    batchOperationColumnFamily.update(this.batchKey, batch);
  }

  @Override
  public void removeKeys(final long batchKey, final BatchOperationExecutionRecord record) {
    LOGGER.trace(
        "Removing keys {} from batch operation with key {}",
        record.getEntityKeys().size(),
        record.getBatchOperationKey());
    final var batch = batchOperationColumnFamily.get(this.batchKey);

    blockKey.wrapLong(batch.getMinBlockKey());
    final var block = batchOperationEntitiesColumnFamily.get(fkBatchKeyAndBlockKey);
    block.removeKeys(record.getEntityKeys());

    if (block.getKeys().isEmpty()) {
      batchOperationEntitiesColumnFamily.deleteExisting(fkBatchKeyAndBlockKey);
      batch.removeBlockKey(block.getKey());
      batchOperationColumnFamily.update(this.batchKey, batch);
    } else {
      batchOperationEntitiesColumnFamily.update(fkBatchKeyAndBlockKey, block);
    }
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

  @Override
  public List<Long> getNextEntityKeys(final long batchOperationKey, final int batchSize) {
    batchKey.wrapLong(batchOperationKey);
    final var batch = batchOperationColumnFamily.get(batchKey);

    if (batch.getMinBlockKey() == -1) {
      return List.of();
    }

    blockKey.wrapLong(batch.getMinBlockKey());
    final var block = batchOperationEntitiesColumnFamily.get(fkBatchKeyAndBlockKey);
    final var blockKeys = block.getKeys();

    return blockKeys.stream().limit(batchSize).toList();
  }

  private PersistedBatchOperationChunk createNewBlock(final PersistedBatchOperation batch) {
    final long currentBlockKey;
    final PersistedBatchOperationChunk batchBlock;
    currentBlockKey = batch.nextBlockKey();
    batchBlock = new PersistedBatchOperationChunk();
    batchBlock.setKey(currentBlockKey).setBatchOperationKey(batch.getKey());
    blockKey.wrapLong(batchBlock.getKey());

    batchOperationEntitiesColumnFamily.insert(fkBatchKeyAndBlockKey, batchBlock);

    return batchBlock;
  }
}
