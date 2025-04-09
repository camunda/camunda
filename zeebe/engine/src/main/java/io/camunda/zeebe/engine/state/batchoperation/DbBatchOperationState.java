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
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbBatchOperationState implements MutableBatchOperationState {

  public static final long MAX_DB_CHUNK_SIZE = 3500;

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
  public void create(final long batchOperationKey, final BatchOperationCreationRecord record) {
    LOGGER.debug("Creating batch operation with key {}", record.getBatchOperationKey());
    batchKey.wrapLong(record.getBatchOperationKey());
    final var batchOperation = new PersistedBatchOperation();
    batchOperation.wrap(record).setStatus(BatchOperationStatus.CREATED);
    batchOperationColumnFamily.upsert(batchKey, batchOperation);
    pendingBatchOperationColumnFamily.upsert(batchKey, DbNil.INSTANCE);
  }

  @Override
  public void start(final long batchOperationKey) {
    LOGGER.debug("Starting batch operation with key {}", batchOperationKey);
    batchKey.wrapLong(batchOperationKey);
    final var batchOperation = get(batchOperationKey);
    if (batchOperation.isPresent()) {
      batchOperation.get().setStatus(BatchOperationStatus.STARTED);
      batchOperationColumnFamily.update(batchKey, batchOperation.get());
      pendingBatchOperationColumnFamily.deleteIfExists(batchKey);
    } else {
      LOGGER.error("Batch operation with key {} not found, cannot start it.", batchOperationKey);
    }
  }

  @Override
  public void fail(final long batchOperationKey) {
    LOGGER.debug("Failing batch operation with key {}", batchOperationKey);
    batchKey.wrapLong(batchOperationKey);
    final var batchOperation = get(batchOperationKey);
    if (batchOperation.isPresent()) {
      batchOperation.get().setStatus(BatchOperationStatus.FAILED);
      batchOperationColumnFamily.update(batchKey, batchOperation.get());
      pendingBatchOperationColumnFamily.deleteIfExists(batchKey);
    } else {
      LOGGER.error("Batch operation with key {} not found, cannot fail it.", batchOperationKey);
    }
  }

  @Override
  public void appendItemKeys(final long batchOperationKey, final Set<Long> itemKeys) {
    LOGGER.trace(
        "Appending {} item keys to batch operation with key {}",
        itemKeys.size(),
        batchOperationKey);

    // First, get the batch operation
    final var batch = get(batchOperationKey);
    if (batch.isEmpty()) {
      LOGGER.error(
          "Batch operation with key {} not found, cannot append itemKeys to it.",
          batchOperationKey);
      return;
    }

    // Second, get the chunk to append the keys to
    var chunk = getOrCreateChunk(batch.get());

    // Third, append the keys to the chunk, if the chunk is full, a new one is returned
    for (final long key : itemKeys) {
      chunk = appendKeyToChunk(batch.get(), chunk, key);
    }

    // Finally, update the batch and the chunk in the column family
    updateChunkAndBatch(chunk, batch.get());
  }

  @Override
  public void removeItemKeys(final long batchOperationKey, final Set<Long> itemKeys) {
    LOGGER.trace(
        "Removing item keys {} from batch operation with key {}",
        itemKeys.size(),
        batchOperationKey);

    // First, get the batch operation
    final var batch = get(batchOperationKey);
    if (batch.isEmpty()) {
      LOGGER.error(
          "Batch operation with key {} not found, cannot remove itemKeys from it.",
          batchOperationKey);
      return;
    }

    // Second, delete the keys from chunk
    final var chunk = getMinChunk(batch.get());
    chunk.removeItemKeys(itemKeys);

    // Finally, update the chunk and batch in the column family
    updateBatchAndChunkAfterRemoval(batch.get(), chunk);
  }

  @Override
  public void cancel(final long batchOperationKey) {
    LOGGER.trace("Cancel batch operation with key {}", batchOperationKey);
    deleteBatchOperation(batchOperationKey);
  }

  @Override
  public void pause(final long batchOperationKey) {
    LOGGER.trace("Pausing batch operation with key {}", batchOperationKey);
    batchKey.wrapLong(batchOperationKey);

    // Set status to PAUSED
    final var batch = batchOperationColumnFamily.get(batchKey);
    batch.setStatus(BatchOperationStatus.PAUSED);
    batchOperationColumnFamily.update(batchKey, batch);
  }

  @Override
  public void complete(final long batchOperationKey) {
    LOGGER.trace("Completing batch operation with key {}", batchOperationKey);
    deleteBatchOperation(batchOperationKey);
  }

  @Override
  public boolean exists(final long batchOperationKey) {
    return get(batchOperationKey).isPresent();
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
    final var batch = get(batchOperationKey);
    if (batch.isEmpty()) {
      LOGGER.error(
          "Batch operation with key {} not found, cannot get next item keys.", batchOperationKey);
      return List.of();
    }

    if (batch.get().getMinChunkKey() == -1) {
      return List.of();
    }

    chunkKey.wrapLong(batch.get().getMinChunkKey());
    final var chunk = batchOperationChunksColumnFamily.get(fkBatchKeyAndChunkKey);
    final var chunkKeys = chunk.getItemKeys();

    return chunkKeys.stream().limit(batchSize).toList();
  }

  /** This deletes everything related to the batch operation. */
  private void deleteBatchOperation(final long batchOperationKey) {
    batchKey.wrapLong(batchOperationKey);

    // first delete the batch operation from the pendingBatchOperationColumnFamily if it exists
    pendingBatchOperationColumnFamily.deleteIfExists(batchKey);

    // then delete all chunks
    batchOperationChunksColumnFamily.whileEqualPrefix(
        batchKey,
        (compositeKey, entityTypeValue) -> {
          batchOperationChunksColumnFamily.deleteExisting(compositeKey);
        });

    // finally delete batch operation itself
    batchOperationColumnFamily.deleteExisting(batchKey);
  }

  private PersistedBatchOperationChunk createNewChunk(final PersistedBatchOperation batch) {
    final var currentChunkKey = batch.nextChunkKey();
    batch.addChunkKey(currentChunkKey);

    final var batchChunk = new PersistedBatchOperationChunk();
    batchChunk.setKey(currentChunkKey).setBatchOperationKey(batch.getKey());
    chunkKey.wrapLong(batchChunk.getKey());

    batchOperationChunksColumnFamily.insert(fkBatchKeyAndChunkKey, batchChunk);

    return batchChunk;
  }

  /** Returns the chunk for min chunk key */
  private PersistedBatchOperationChunk getMinChunk(final PersistedBatchOperation batch) {
    chunkKey.wrapLong(batch.getMinChunkKey());
    return batchOperationChunksColumnFamily.get(fkBatchKeyAndChunkKey);
  }

  /**
   * Updates the batch and chunk after removing keys from the chunk. If the chunk is empty, it is
   * deleted from the column family and the batch operation is updated.
   *
   * @param chunk the chunk to update
   * @param batch the batch operation to update
   */
  private void updateBatchAndChunkAfterRemoval(
      final PersistedBatchOperation batch, final PersistedBatchOperationChunk chunk) {
    if (chunk.getItemKeys().isEmpty()) {
      batchOperationChunksColumnFamily.deleteExisting(fkBatchKeyAndChunkKey);
      batch.removeChunkKey(chunk.getKey());
      batchOperationColumnFamily.update(batchKey, batch);
    } else {
      batchOperationChunksColumnFamily.update(fkBatchKeyAndChunkKey, chunk);
    }
  }

  /**
   * Gets the chunk for the batch operation. If the batch operation has no chunk (currentChunkKey =
   * -1), a new one is created.
   *
   * @param batch the batch operation to get the chunk for
   * @return the chunk for the batch operation
   */
  private PersistedBatchOperationChunk getOrCreateChunk(final PersistedBatchOperation batch) {
    final var currentChunkKey = batch.getMinChunkKey();
    if (currentChunkKey == -1) {
      return createNewChunk(batch);
    } else {
      chunkKey.wrapLong(currentChunkKey);
      return batchOperationChunksColumnFamily.get(fkBatchKeyAndChunkKey);
    }
  }

  /**
   * Appends the key to the chunk. If the chunk is full, a new one is created.
   *
   * @param chunk the current chunk to append the keys to, if it is full, a new one is created
   * @param key the key to append
   * @return the current updated chunk
   */
  private PersistedBatchOperationChunk appendKeyToChunk(
      final PersistedBatchOperation batch, PersistedBatchOperationChunk chunk, final long key) {
    if (chunk.getItemKeys().size() >= MAX_DB_CHUNK_SIZE) {
      batchOperationChunksColumnFamily.update(fkBatchKeyAndChunkKey, chunk);
      chunk = createNewChunk(batch);
    }

    chunk.appendItemKey(key);
    return chunk;
  }

  private void updateChunkAndBatch(
      final PersistedBatchOperationChunk chunk, final PersistedBatchOperation batch) {
    if (chunk != null) {
      batchOperationChunksColumnFamily.update(fkBatchKeyAndChunkKey, chunk);
    }
    batchOperationColumnFamily.update(batchKey, batch);
  }
}
