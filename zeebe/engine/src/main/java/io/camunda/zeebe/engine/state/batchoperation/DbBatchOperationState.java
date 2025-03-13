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
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationState;
import io.camunda.zeebe.engine.state.mutable.MutableBatchOperationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationSubbatchRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbBatchOperationState implements MutableBatchOperationState {

  private static final String KEY = "BATCH_OPERATION";
  private final Logger LOGGER = LoggerFactory.getLogger(DbBatchOperationState.class);
  private final DbLong batchKey = new DbLong();
  private final DbForeignKey<DbLong> fkBatchKey;
  private final DbLong chunkKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbLong> fkBatchKeyAndChunkKey;

  private final ColumnFamily<DbLong, PersistedBatchOperation> batchOperationColumnFamily;
  private final ColumnFamily<
          DbCompositeKey<DbForeignKey<DbLong>, DbLong>, PersistedBatchOperationChunk>
      batchOperationEntitiesColumnFamily;
  private final ColumnFamily<DbLong, DbNil> pendingBatchOperationColumnFamily;

  public DbBatchOperationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    batchOperationColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BATCH_OPERATION,
            transactionContext,
            batchKey,
            new PersistedBatchOperation());
    fkBatchKey = new DbForeignKey<>(batchKey, ZbColumnFamilies.BATCH_OPERATION);
    chunkKey = new DbLong();
    fkBatchKeyAndChunkKey = new DbCompositeKey<>(fkBatchKey, chunkKey);
    batchOperationEntitiesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ENTITY_BY_BATCH_OPERATION,
            transactionContext,
            fkBatchKeyAndChunkKey,
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
        .setStatus(BatchOperationState.ACTIVATED)
        .setBatchOperationType(record.getBatchOperationType())
        .setIntent(BatchOperationIntent.CREATED)
        .setFilter(record.getFilterBuffer())
        .setOffset(0);
    batchOperationColumnFamily.insert(this.batchKey, batchOperation);
    pendingBatchOperationColumnFamily.upsert(this.batchKey, DbNil.INSTANCE);
  }

  @Override
  public void removeFromPending(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(record.getBatchOperationKey());
    pendingBatchOperationColumnFamily.deleteIfExists(this.batchKey);
  }

  @Override
  public void appendKeys(final long batchKey, final BatchOperationSubbatchRecord record) {
    LOGGER.debug(
        "Appending {} keys to batch operation with key {}",
        record.getKeys().size(),
        record.getBatchOperationKey());
    final var batch = batchOperationColumnFamily.get(this.batchKey);

    PersistedBatchOperationChunk chunk = null;
    for (final long key : record.getKeys()) {
      final var currentChunkKey = batch.getMinChunkKey();
      if (currentChunkKey == -1) {
        chunk = createNewChunk(batch);
        LOGGER.debug(
            "Creating new chunk with key {} for batch operation with key {}",
            chunk.getKey(),
            record.getBatchOperationKey());
      } else if (chunk == null) {
        LOGGER.debug(
            "Loading chunk with key {} for batch operation with key {}",
            currentChunkKey,
            record.getBatchOperationKey());
        chunk = batchOperationEntitiesColumnFamily.get(fkBatchKeyAndChunkKey);
        chunkKey.wrapLong(chunk.getKey());
      }
      if (chunk.getKeys().size() >= 15) {
        LOGGER.debug(
            "Chunk with key {} is full, inserting it and creating new chunk for batch operation with key {}",
            chunk.getKey(),
            record.getBatchOperationKey());
        batchOperationEntitiesColumnFamily.update(fkBatchKeyAndChunkKey, chunk);
        chunk = createNewChunk(batch);
      }
      chunk.appendKey(key);
    }
    if (chunk != null) {
      batchOperationEntitiesColumnFamily.update(fkBatchKeyAndChunkKey, chunk);
    }

    batchOperationColumnFamily.update(this.batchKey, batch);
  }

  @Override
  public void removeKeys(final long batchKey, final BatchOperationExecutionRecord record) {
    LOGGER.trace(
        "Removing keys {} from batch operation with key {}",
        record.getKeys().size(),
        record.getBatchOperationKey());
    final var batch = batchOperationColumnFamily.get(this.batchKey);
    if (batch == null) {
      LOGGER.warn(
          "Batch operation with key {} not found for removing entity keys",
          record.getBatchOperationKey());
      return;
    }

    chunkKey.wrapLong(batch.getMinChunkKey());
    final var chunk = batchOperationEntitiesColumnFamily.get(fkBatchKeyAndChunkKey);
    chunk.removeKeys(record.getKeys());

    if (chunk.getKeys().isEmpty()) {
      batchOperationEntitiesColumnFamily.deleteExisting(fkBatchKeyAndChunkKey);
      batch.removeChunkKey(chunk.getKey());
      batchOperationColumnFamily.update(this.batchKey, batch);
    } else {
      batchOperationEntitiesColumnFamily.update(fkBatchKeyAndChunkKey, chunk);
    }
  }

  @Override
  public void pause(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(record.getBatchOperationKey());
    final var batch = batchOperationColumnFamily.get(this.batchKey);
    if (batch == null) {
      LOGGER.warn(
          "Batch operation with key {} not found for pausing", record.getBatchOperationKey());
      return;
    }
    batch.setStatus(BatchOperationState.PAUSED);
    batchOperationColumnFamily.update(this.batchKey, batch);
  }

  @Override
  public void resume(final long batchKey, final BatchOperationExecutionRecord record) {
    this.batchKey.wrapLong(record.getBatchOperationKey());
    final var batch = batchOperationColumnFamily.get(this.batchKey);
    if (batch == null) {
      LOGGER.warn(
          "Batch operation with key {} not found for resuming", record.getBatchOperationKey());
      return;
    }
    batch.setStatus(BatchOperationState.ACTIVATED);
    batchOperationColumnFamily.update(this.batchKey, batch);
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

  private PersistedBatchOperationChunk createNewChunk(final PersistedBatchOperation batch) {
    final long currentChunkKey;
    final PersistedBatchOperationChunk batchChunk;
    currentChunkKey = batch.nextChunkKey();
    batchChunk = new PersistedBatchOperationChunk();
    batchChunk.setKey(currentChunkKey).setBatchOperationKey(batch.getKey());
    chunkKey.wrapLong(batchChunk.getKey());

    batchOperationEntitiesColumnFamily.insert(fkBatchKeyAndChunkKey, batchChunk);

    return batchChunk;
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

    if (batch.getMinChunkKey() == -1) {
      return List.of();
    }

    chunkKey.wrapLong(batch.getMinChunkKey());
    final var chunk = batchOperationEntitiesColumnFamily.get(fkBatchKeyAndChunkKey);
    final var chunkKeys = chunk.getKeys();

    return chunkKeys.stream().limit(batchSize).toList();
  }
}
