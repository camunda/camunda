/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

import java.util.UUID;

public class OperationExecutorProperties {

  public static final int BATCH_SIZE_DEFAULT = 500;

  public static final String WORKER_ID_DEFAULT = UUID.randomUUID().toString();

  public static final long LOCK_TIMEOUT_DEFAULT = 60000L;   // 1 min

  private static final int DEFAULT_IMPORT_THREADS_COUNT = 3;

  private static final int DEFAULT_IMPORT_QUEUE_SIZE = 10;

  /**
   * Amount of process instances, that will be processed by one run of operation executor. This counts process instances, but can end up in more operations,
   * as one process instance can have more than one scheduled operations.
   */
  private int batchSize = BATCH_SIZE_DEFAULT;

  private String workerId = WORKER_ID_DEFAULT;

  /**
   * Milliseconds.
   */
  private long lockTimeout = LOCK_TIMEOUT_DEFAULT;

  private boolean executorEnabled = true;

  private int threadsCount = DEFAULT_IMPORT_THREADS_COUNT;

  private int queueSize = DEFAULT_IMPORT_QUEUE_SIZE;

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public String getWorkerId() {
    return workerId;
  }

  public void setWorkerId(String workerId) {
    this.workerId = workerId;
  }

  public long getLockTimeout() {
    return lockTimeout;
  }

  public void setLockTimeout(long lockTimeout) {
    this.lockTimeout = lockTimeout;
  }

  public boolean isExecutorEnabled() {
    return executorEnabled;
  }

  public void setExecutorEnabled(boolean executorEnabled) {
    this.executorEnabled = executorEnabled;
  }

  public int getThreadsCount() {
    return threadsCount;
  }

  public void setThreadsCount(int threadsCount) {
    this.threadsCount = threadsCount;
  }

  public int getQueueSize() {
    return queueSize;
  }

  public void setQueueSize(int queueSize) {
    this.queueSize = queueSize;
  }
}
