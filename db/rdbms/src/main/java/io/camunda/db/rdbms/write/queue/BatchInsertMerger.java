/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BatchInsertMerger<M> implements QueueItemMerger {

  private static final Logger LOG = LoggerFactory.getLogger(BatchInsertMerger.class);

  private final ContextType contextType;
  private final M model;
  private final int maxBatchSize;
  private final Function<M, Object> keyExtractor;

  protected BatchInsertMerger(
      final ContextType contextType,
      final M model,
      final int maxBatchSize,
      final Function<M, Object> keyExtractor) {
    this.contextType = contextType;
    this.model = model;
    this.maxBatchSize = maxBatchSize;
    this.keyExtractor = keyExtractor;
  }

  @Override
  public boolean canBeMerged(final QueueItem queueItem) {
    if (queueItem.contextType() != contextType
        || queueItem.statementType() != WriteStatementType.INSERT
        || !(queueItem.parameter() instanceof BatchInsertDto)) {
      return false;
    }

    final var batch = (BatchInsertDto<M>) queueItem.parameter();
    // If the model is already present in this batch, claim the item regardless of the batch size
    // limit so that merge() can drop it as a no-op. Otherwise the writer would fall back to
    // executeInQueue and create a second batch item holding the duplicate, which still violates the
    // primary key when flushed.
    if (containsModelKey(batch)) {
      return true;
    }

    if (maxBatchSize == 1) {
      return false;
    }
    return batch.dbModels().size() < maxBatchSize;
  }

  @Override
  public QueueItem merge(final QueueItem originalItem) {
    final var batch = (BatchInsertDto<M>) originalItem.parameter();
    if (containsModelKey(batch)) {
      // The exporter may re-process and re-enqueue the same record after a failed flush. Dropping
      // the duplicate here keeps the same primary key from appearing twice in one batch INSERT.
      LOG.warn(
          "Dropping duplicate {} insert for key {} that is already queued in the current batch",
          contextType,
          keyExtractor.apply(model));
      return originalItem;
    }
    return originalItem.copy(b -> b.parameter(batch.withAdditionalDbModel(model)));
  }

  private boolean containsModelKey(final BatchInsertDto<M> batch) {
    final Object key = keyExtractor.apply(model);
    return batch.dbModels().stream().anyMatch(m -> Objects.equals(keyExtractor.apply(m), key));
  }
}
