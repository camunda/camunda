/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.util.ObjectBuilder;
import java.time.Duration;

public record RdbmsWriterConfig(
    int partitionId,
    int maxQueueSize,
    Duration defaultHistoryTTL,
    Duration minHistoryCleanupInterval,
    Duration maxHistoryCleanupInterval,
    int historyCleanupBatchSize,
    /*
     * The number of batch operation items to insert in a single insert statement.
     */
    int batchOperationItemInsertBlockSize,
    /*
     * Export the batch operation items when the initial chunk records are processed. If set to
     * <code>false</code>, the batch operation items will be exported only when they have been
     * processed and are completed or failed.
     */
    boolean exportBatchOperationItemsOnCreation) {

  public static final int DEFAULT_QUEUE_SIZE = -1;
  public static final Duration DEFAULT_HISTORY_TTL = Duration.ofDays(30);
  public static final Duration DEFAULT_MIN_HISTORY_CLEANUP_INTERVAL = Duration.ofMinutes(1);
  public static final Duration DEFAULT_MAX_HISTORY_CLEANUP_INTERVAL = Duration.ofMinutes(60);
  public static final int DEFAULT_HISTORY_CLEANUP_BATCH_SIZE = 1000;
  public static final int DEFAULT_BATCH_OPERATION_ITEM_INSERT_BLOCK_SIZE = 10000;
  public static final boolean DEFAULT_EXPORT_BATCH_OPERATION_ITEMS_ON_CREATION = true;

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder implements ObjectBuilder<RdbmsWriterConfig> {

    private int partitionId;
    private int maxQueueSize = DEFAULT_QUEUE_SIZE;
    private Duration defaultHistoryTTL = DEFAULT_HISTORY_TTL;
    private Duration minHistoryCleanupInterval = DEFAULT_MIN_HISTORY_CLEANUP_INTERVAL;
    private Duration maxHistoryCleanupInterval = DEFAULT_MAX_HISTORY_CLEANUP_INTERVAL;
    private int historyCleanupBatchSize = DEFAULT_HISTORY_CLEANUP_BATCH_SIZE;
    private int batchOperationItemInsertBlockSize = DEFAULT_BATCH_OPERATION_ITEM_INSERT_BLOCK_SIZE;
    private boolean exportBatchOperationItemsOnCreation =
        DEFAULT_EXPORT_BATCH_OPERATION_ITEMS_ON_CREATION;

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder maxQueueSize(final int maxQueueSize) {
      this.maxQueueSize = maxQueueSize;
      return this;
    }

    public Builder defaultHistoryTTL(final Duration defaultHistoryTTL) {
      this.defaultHistoryTTL = defaultHistoryTTL;
      return this;
    }

    public Builder minHistoryCleanupInterval(final Duration minHistoryCleanupInterval) {
      this.minHistoryCleanupInterval = minHistoryCleanupInterval;
      return this;
    }

    public Builder maxHistoryCleanupInterval(final Duration maxHistoryCleanupInterval) {
      this.maxHistoryCleanupInterval = maxHistoryCleanupInterval;
      return this;
    }

    public Builder historyCleanupBatchSize(final int historyCleanupBatchSize) {
      this.historyCleanupBatchSize = historyCleanupBatchSize;
      return this;
    }

    public Builder batchOperationItemInsertBlockSize(final int batchOperationItemInsertBlockSize) {
      this.batchOperationItemInsertBlockSize = batchOperationItemInsertBlockSize;
      return this;
    }

    public Builder exportBatchOperationItemsOnCreation(
        final boolean exportBatchOperationItemsOnCreation) {
      this.exportBatchOperationItemsOnCreation = exportBatchOperationItemsOnCreation;
      return this;
    }

    @Override
    public RdbmsWriterConfig build() {
      return new RdbmsWriterConfig(
          partitionId,
          maxQueueSize,
          defaultHistoryTTL,
          minHistoryCleanupInterval,
          maxHistoryCleanupInterval,
          historyCleanupBatchSize,
          batchOperationItemInsertBlockSize,
          exportBatchOperationItemsOnCreation);
    }
  }
}
