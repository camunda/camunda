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
    int queueSize,
    /*
     * The number of batch operation items to insert in a single insert statement.
     */
    int batchOperationItemInsertBlockSize,
    /*
     * Export the batch operation items when the initial chunk records are processed. If set to
     * <code>false</code>, the batch operation items will be exported only when they have been
     * processed and are completed or failed.
     */
    boolean exportBatchOperationItemsOnCreation,
    HistoryConfig history) {

  public static final int DEFAULT_QUEUE_SIZE = 1000;
  public static final int DEFAULT_BATCH_OPERATION_ITEM_INSERT_BLOCK_SIZE = 10000;
  public static final boolean DEFAULT_EXPORT_BATCH_OPERATION_ITEMS_ON_CREATION = true;

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder implements ObjectBuilder<RdbmsWriterConfig> {

    private int partitionId;
    private int queueSize = DEFAULT_QUEUE_SIZE;
    private int batchOperationItemInsertBlockSize = DEFAULT_BATCH_OPERATION_ITEM_INSERT_BLOCK_SIZE;
    private boolean exportBatchOperationItemsOnCreation =
        DEFAULT_EXPORT_BATCH_OPERATION_ITEMS_ON_CREATION;
    private HistoryConfig history = new HistoryConfig.Builder().build();

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder queueSize(final int queueSize) {
      this.queueSize = queueSize;
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

    public Builder history(final HistoryConfig history) {
      this.history = history;
      return this;
    }

    @Override
    public RdbmsWriterConfig build() {
      return new RdbmsWriterConfig(
          partitionId,
          queueSize,
          batchOperationItemInsertBlockSize,
          exportBatchOperationItemsOnCreation,
          history);
    }
  }

  public record HistoryConfig(
      Duration defaultHistoryTTL,
      Duration batchOperationCancelProcessInstanceHistoryTTL,
      Duration batchOperationMigrateProcessInstanceHistoryTTL,
      Duration batchOperationModifyProcessInstanceHistoryTTL,
      Duration batchOperationResolveIncidentHistoryTTL,
      Duration minHistoryCleanupInterval,
      Duration maxHistoryCleanupInterval,
      int historyCleanupBatchSize) {

    public static final Duration DEFAULT_HISTORY_TTL = Duration.ofDays(30);
    public static final Duration DEFAULT_BATCH_OPERATION_HISTORY_TTL = Duration.ofDays(5);
    public static final Duration DEFAULT_MIN_HISTORY_CLEANUP_INTERVAL = Duration.ofMinutes(1);
    public static final Duration DEFAULT_MAX_HISTORY_CLEANUP_INTERVAL = Duration.ofMinutes(60);
    public static final int DEFAULT_HISTORY_CLEANUP_BATCH_SIZE = 1000;

    public static RdbmsWriterConfig.Builder builder() {
      return new RdbmsWriterConfig.Builder();
    }

    public static class Builder implements ObjectBuilder<HistoryConfig> {

      private Duration defaultHistoryTTL = DEFAULT_HISTORY_TTL;
      private Duration batchOperationCancelProcessInstanceHistoryTTL =
          DEFAULT_BATCH_OPERATION_HISTORY_TTL;
      private Duration batchOperationMigrateProcessInstanceHistoryTTL =
          DEFAULT_BATCH_OPERATION_HISTORY_TTL;
      private Duration batchOperationModifyProcessInstanceHistoryTTL =
          DEFAULT_BATCH_OPERATION_HISTORY_TTL;
      private Duration batchOperationResolveIncidentHistoryTTL =
          DEFAULT_BATCH_OPERATION_HISTORY_TTL;
      private Duration minHistoryCleanupInterval = DEFAULT_MIN_HISTORY_CLEANUP_INTERVAL;
      private Duration maxHistoryCleanupInterval = DEFAULT_MAX_HISTORY_CLEANUP_INTERVAL;
      private int historyCleanupBatchSize = DEFAULT_HISTORY_CLEANUP_BATCH_SIZE;

      public HistoryConfig.Builder defaultHistoryTTL(final Duration defaultHistoryTTL) {
        this.defaultHistoryTTL = defaultHistoryTTL;
        return this;
      }

      public HistoryConfig.Builder batchOperationCancelProcessInstanceHistoryTTL(
          final Duration batchOperationCancelProcessInstanceHistoryTTL) {
        this.batchOperationCancelProcessInstanceHistoryTTL =
            batchOperationCancelProcessInstanceHistoryTTL;
        return this;
      }

      public HistoryConfig.Builder batchOperationMigrateProcessInstanceHistoryTTL(
          final Duration batchOperationMigrateProcessInstanceHistoryTTL) {
        this.batchOperationMigrateProcessInstanceHistoryTTL =
            batchOperationMigrateProcessInstanceHistoryTTL;
        return this;
      }

      public HistoryConfig.Builder batchOperationModifyProcessInstanceHistoryTTL(
          final Duration batchOperationModifyProcessInstanceHistoryTTL) {
        this.batchOperationModifyProcessInstanceHistoryTTL =
            batchOperationModifyProcessInstanceHistoryTTL;
        return this;
      }

      public HistoryConfig.Builder batchOperationResolveIncidentHistoryTTL(
          final Duration batchOperationResolveIncidentHistoryTTL) {
        this.batchOperationResolveIncidentHistoryTTL = batchOperationResolveIncidentHistoryTTL;
        return this;
      }

      public HistoryConfig.Builder minHistoryCleanupInterval(
          final Duration minHistoryCleanupInterval) {
        this.minHistoryCleanupInterval = minHistoryCleanupInterval;
        return this;
      }

      public HistoryConfig.Builder maxHistoryCleanupInterval(
          final Duration maxHistoryCleanupInterval) {
        this.maxHistoryCleanupInterval = maxHistoryCleanupInterval;
        return this;
      }

      public HistoryConfig.Builder historyCleanupBatchSize(final int historyCleanupBatchSize) {
        this.historyCleanupBatchSize = historyCleanupBatchSize;
        return this;
      }

      @Override
      public HistoryConfig build() {
        return new HistoryConfig(
            defaultHistoryTTL,
            batchOperationCancelProcessInstanceHistoryTTL,
            batchOperationMigrateProcessInstanceHistoryTTL,
            batchOperationModifyProcessInstanceHistoryTTL,
            batchOperationResolveIncidentHistoryTTL,
            minHistoryCleanupInterval,
            maxHistoryCleanupInterval,
            historyCleanupBatchSize);
      }
    }
  }
}
