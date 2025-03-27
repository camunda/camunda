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

  public static final long MAX_CHUNK_SIZE = 3500;

  private static final Logger LOGGER = LoggerFactory.getLogger(DbBatchOperationState.class);

  private final DbLong batchKey = new DbLong();
  private final DbForeignKey<DbLong> fkBatchKey;
  private final DbLong chunkKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbLong> fkBatchKeyAndChunkKey;

  private final ColumnFamily<DbLong, PersistedBatchOperation> batchOperationColumnFamily;
  private final ColumnFamily<
          DbCompositeKey<DbForeignKey<DbLong>, DbLong>, PersistedBatchOperationChunk>
      batchOperationChunksColumnFamily;
  private final ColumnFamily<DbLong, DbNil> pendingBatchOperationColumnFamily;

  public DbBatchOperationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    fkBatchKey = new DbForeignKey<>(batchKey, ZbColumnFamilies.BATCH_OPERATION);
    chunkKey = new DbLong();
    fkBatchKeyAndChunkKey = new DbCompositeKey<>(fkBatchKey, chunkKey);

    batchOperationColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BATCH_OPERATION,
            transactionContext,
            batchKey,
            new PersistedBatchOperation());
    batchOperationChunksColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.BATCH_OPERATION_CHUNKS,
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
        .setStatus(BatchOperationStatus.CREATED)
        .setBatchOperationType(record.getBatchOperationType())
        .setEntityFilter(record.getEntityFilterBuffer());
    batchOperationColumnFamily.upsert(this.batchKey, batchOperation);
    pendingBatchOperationColumnFamily.upsert(this.batchKey, DbNil.INSTANCE);
  }

  @Override
  public void appendItemKeys(final long batchKey, final BatchOperationChunkRecord record) {
    LOGGER.trace(
        "Appending {} item keys to batch operation with key {}",
        record.getItemKeys().size(),
        record.getBatchOperationKey());
    this.batchKey.wrapLong(batchKey);
    final var batch = batchOperationColumnFamily.get(this.batchKey);

    pendingBatchOperationColumnFamily.deleteIfExists(this.batchKey);

    PersistedBatchOperationChunk chunk = null;
    for (final long key : record.getItemKeys()) {
      final var currentChunkKey = batch.getMinChunkKey();
      if (currentChunkKey == -1) {
        chunk = createNewChunk(batch);
        LOGGER.trace(
            "Creating new chunk with key {} for batch operation with key {}",
            chunk.getKey(),
            record.getBatchOperationKey());
      } else if (chunk == null) {
        LOGGER.trace(
            "Loading chunk with key {} for batch operation with key {}",
            currentChunkKey,
            record.getBatchOperationKey());
        chunk = batchOperationChunksColumnFamily.get(fkBatchKeyAndChunkKey);
        chunkKey.wrapLong(chunk.getKey());
      }
      if (chunk.getItemKeys().size() >= MAX_CHUNK_SIZE) {
        LOGGER.trace(
            "Chunk with key {} is full, inserting it and creating new chunk for batch operation with key {}",
            chunk.getKey(),
            record.getBatchOperationKey());
        batchOperationChunksColumnFamily.update(fkBatchKeyAndChunkKey, chunk);
        chunk = createNewChunk(batch);
      }
      chunk.appendItemKey(key);
    }
    if (chunk != null) {
      batchOperationChunksColumnFamily.update(fkBatchKeyAndChunkKey, chunk);
    }

    batchOperationColumnFamily.update(this.batchKey, batch);
  }

  @Override
  public void removeItemKeys(final long batchKey, final BatchOperationExecutionRecord record) {
    LOGGER.trace(
        "Removing item keys {} from batch operation with key {}",
        record.getItemKeys().size(),
        record.getBatchOperationKey());
    this.batchKey.wrapLong(batchKey);
    final var batch = batchOperationColumnFamily.get(this.batchKey);

    chunkKey.wrapLong(batch.getMinChunkKey());
    final var chunk = batchOperationChunksColumnFamily.get(fkBatchKeyAndChunkKey);
    chunk.removeItemKeys(record.getItemKeys());

    if (chunk.getItemKeys().isEmpty()) {
      batchOperationChunksColumnFamily.deleteExisting(fkBatchKeyAndChunkKey);
      batch.removeChunkKey(chunk.getKey());
      batchOperationColumnFamily.update(this.batchKey, batch);
    } else {
      batchOperationChunksColumnFamily.update(fkBatchKeyAndChunkKey, chunk);
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
  public List<Long> getNextItemKeys(final long batchOperationKey, final int batchSize) {
    batchKey.wrapLong(batchOperationKey);
    final var batch = batchOperationColumnFamily.get(batchKey);

    if (batch.getMinChunkKey() == -1) {
      return List.of();
    }

    chunkKey.wrapLong(batch.getMinChunkKey());
    final var chunk = batchOperationChunksColumnFamily.get(fkBatchKeyAndChunkKey);
    final var chunkKeys = chunk.getItemKeys();

    return chunkKeys.stream().limit(batchSize).toList();
  }

  private PersistedBatchOperationChunk createNewChunk(final PersistedBatchOperation batch) {
    final long currentChunkKey;
    final PersistedBatchOperationChunk batchChunk;
    currentChunkKey = batch.nextChunkKey();
    batchChunk = new PersistedBatchOperationChunk();
    batchChunk.setKey(currentChunkKey).setBatchOperationKey(batch.getKey());
    chunkKey.wrapLong(batchChunk.getKey());

    batchOperationChunksColumnFamily.insert(fkBatchKeyAndChunkKey, batchChunk);

    return batchChunk;
  }
}
