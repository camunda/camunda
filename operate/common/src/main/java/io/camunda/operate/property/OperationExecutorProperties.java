/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

import java.util.UUID;

public class OperationExecutorProperties {

  public static final int BATCH_SIZE_DEFAULT = 500;
  public static final int DELETION_BATCH_SIZE_DEFAULT = 5000;

  public static final String WORKER_ID_DEFAULT = UUID.randomUUID().toString();

  public static final long LOCK_TIMEOUT_DEFAULT = 60000L; // 1 min

  private static final int DEFAULT_IMPORT_THREADS_COUNT = 3;

  private static final int DEFAULT_IMPORT_QUEUE_SIZE = 10;
  private static final int DEFAULT_MODIFY_TOKENS_LIMIT = 500;

  /**
   * Amount of process instances, that will be processed by one run of operation executor. This
   * counts process instances, but can end up in more operations, as one process instance can have
   * more than one scheduled operations.
   */
  private int batchSize = BATCH_SIZE_DEFAULT;

  private int deletionBatchSize = DELETION_BATCH_SIZE_DEFAULT;

  private String workerId = WORKER_ID_DEFAULT;

  /** Milliseconds. */
  private long lockTimeout = LOCK_TIMEOUT_DEFAULT;

  private boolean executorEnabled = true;

  private int threadsCount = DEFAULT_IMPORT_THREADS_COUNT;

  private int queueSize = DEFAULT_IMPORT_QUEUE_SIZE;

  /**
   * Maximum number of flow node instances (tokens) that can be canceled or moved in a single
   * process-instance modification for one element ID. Requests above this limit are truncated to
   * avoid Zeebe command timeouts.
   */
  private int maxModifyTokensLimit = DEFAULT_MODIFY_TOKENS_LIMIT;

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public int getDeletionBatchSize() {
    return deletionBatchSize;
  }

  public void setDeletionBatchSize(final int deletionBatchSize) {
    this.deletionBatchSize = deletionBatchSize;
  }

  public String getWorkerId() {
    return workerId;
  }

  public void setWorkerId(final String workerId) {
    this.workerId = workerId;
  }

  public long getLockTimeout() {
    return lockTimeout;
  }

  public void setLockTimeout(final long lockTimeout) {
    this.lockTimeout = lockTimeout;
  }

  public boolean isExecutorEnabled() {
    return executorEnabled;
  }

  public void setExecutorEnabled(final boolean executorEnabled) {
    this.executorEnabled = executorEnabled;
  }

  public int getThreadsCount() {
    return threadsCount;
  }

  public void setThreadsCount(final int threadsCount) {
    this.threadsCount = threadsCount;
  }

  public int getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(final int queueSize) {
    this.queueSize = queueSize;
  }

  public int getMaxModifyTokensLimit() {
    return maxModifyTokensLimit;
  }

  public void setMaxModifyTokensLimit(final int maxModifyTokensLimit) {
    this.maxModifyTokensLimit = maxModifyTokensLimit;
  }
}
