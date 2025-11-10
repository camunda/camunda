/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;
import java.time.Duration;

public class BatchOperationCfg implements ConfigurationEntry {
  private Duration schedulerInterval =
      EngineConfiguration.DEFAULT_BATCH_OPERATION_SCHEDULER_INTERVAL;

  /** Number of itemKeys in one BatchOperationChunkRecord. Must be below 4MB total record size. */
  private int chunkSize = EngineConfiguration.DEFAULT_BATCH_OPERATION_CHUNK_SIZE;

  private int queryPageSize = EngineConfiguration.DEFAULT_BATCH_OPERATION_QUERY_PAGE_SIZE;
  private int queryInClauseSize = EngineConfiguration.DEFAULT_BATCH_OPERATION_QUERY_IN_CLAUSE_SIZE;

  private int queryRetryMax = EngineConfiguration.DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX;
  private Duration queryRetryInitialDelay =
      EngineConfiguration.DEFAULT_BATCH_OPERATION_QUERY_RETRY_INITIAL_DELAY;
  private Duration queryRetryMaxDelay =
      EngineConfiguration.DEFAULT_BATCH_OPERATION_QUERY_RETRY_MAX_DELAY;
  private int queryRetryBackoffFactor =
      EngineConfiguration.DEFAULT_BATCH_OPERATION_QUERY_RETRY_BACKOFF_FACTOR;

  public Duration getSchedulerInterval() {
    return schedulerInterval;
  }

  public void setSchedulerInterval(final Duration schedulerInterval) {
    this.schedulerInterval = schedulerInterval;
  }

  public int getChunkSize() {
    return chunkSize;
  }

  public void setChunkSize(final int chunkSize) {
    this.chunkSize = chunkSize;
  }

  public int getQueryPageSize() {
    return queryPageSize;
  }

  public void setQueryPageSize(final int queryPageSize) {
    this.queryPageSize = queryPageSize;
  }

  public int getQueryInClauseSize() {
    return queryInClauseSize;
  }

  public void setQueryInClauseSize(final int queryInClauseSize) {
    this.queryInClauseSize = queryInClauseSize;
  }

  public int getQueryRetryMax() {
    return queryRetryMax;
  }

  public void setQueryRetryMax(final int queryRetryMax) {
    this.queryRetryMax = queryRetryMax;
  }

  public Duration getQueryRetryInitialDelay() {
    return queryRetryInitialDelay;
  }

  public void setQueryRetryInitialDelay(final Duration queryRetryMaxDelay) {
    queryRetryInitialDelay = queryRetryMaxDelay;
  }

  public Duration getQueryRetryMaxDelay() {
    return queryRetryMaxDelay;
  }

  public void setQueryRetryMaxDelay(final Duration queryRetryMaxDelay) {
    this.queryRetryMaxDelay = queryRetryMaxDelay;
  }

  public int getQueryRetryBackoffFactor() {
    return queryRetryBackoffFactor;
  }

  public void setQueryRetryBackoffFactor(final int queryRetryBackoffFactor) {
    this.queryRetryBackoffFactor = queryRetryBackoffFactor;
  }

  @Override
  public String toString() {
    return "BatchOperationCfg{"
        + "schedulerInterval="
        + schedulerInterval
        + ", chunkSize="
        + chunkSize
        + ", queryPageSize="
        + queryPageSize
        + ", queryInClauseSize="
        + queryInClauseSize
        + ", queryRetryMax="
        + queryRetryMax
        + ", queryRetryInitialDelay="
        + queryRetryInitialDelay
        + ", queryRetryMaxDelay="
        + queryRetryMaxDelay
        + ", queryRetryBackoffFactor="
        + queryRetryBackoffFactor
        + '}';
  }
}
