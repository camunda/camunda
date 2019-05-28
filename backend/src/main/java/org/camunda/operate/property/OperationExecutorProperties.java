/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.property;

import java.util.UUID;

public class OperationExecutorProperties {

  public static final int BATCH_SIZE_DEFAULT = 2000;

  public static final String WORKER_ID_DEFAULT = UUID.randomUUID().toString();

  public static final long LOCK_TIMEOUT_DEFAULT = 60000L;   // 1 min

  /**
   * Amount of workflow instances, that will be processed by one run of operation executor. This counts workflow instances, but can end up in more operations,
   * as one workflow instance can have more than one scheduled operations.
   */
  private int batchSize = BATCH_SIZE_DEFAULT;

  private String workerId = WORKER_ID_DEFAULT;

  /**
   * Milliseconds.
   */
  private long lockTimeout = LOCK_TIMEOUT_DEFAULT;

  private boolean executorEnabled = true;

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
}
