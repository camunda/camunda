/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.property;

public class ImportProperties {

  private static final int DEFAULT_IMPORT_THREADS_COUNT = 3;

  private static final int DEFAULT_IMPORT_QUEUE_SIZE = 5;

  private static final int DEFAULT_READER_BACKOFF = 5000;

  private static final int DEFAULT_SCHEDULER_BACKOFF = 5000;

  private int threadsCount = DEFAULT_IMPORT_THREADS_COUNT;

  private int queueSize = DEFAULT_IMPORT_QUEUE_SIZE;

  private int readerBackoff = DEFAULT_READER_BACKOFF;

  private int schedulerBackoff = DEFAULT_SCHEDULER_BACKOFF;

  /** Indicates, whether loading of Zeebe data should start on startup. */
  private boolean startLoadingDataOnStartup = true;

  public boolean isStartLoadingDataOnStartup() {
    return startLoadingDataOnStartup;
  }

  public void setStartLoadingDataOnStartup(boolean startLoadingDataOnStartup) {
    this.startLoadingDataOnStartup = startLoadingDataOnStartup;
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

  public int getReaderBackoff() {
    return readerBackoff;
  }

  public void setReaderBackoff(int readerBackoff) {
    this.readerBackoff = readerBackoff;
  }

  public int getSchedulerBackoff() {
    return schedulerBackoff;
  }

  public void setSchedulerBackoff(int schedulerBackoff) {
    this.schedulerBackoff = schedulerBackoff;
  }
}
