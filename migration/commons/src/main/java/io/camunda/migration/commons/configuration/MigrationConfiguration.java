/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.commons.configuration;

import io.camunda.zeebe.util.retry.RetryConfiguration;
import java.time.Duration;

public class MigrationConfiguration {
  private int batchSize = 20;
  private Duration importerFinishedTimeout = Duration.ofMinutes(1);
  private Duration timeout = Duration.ofHours(2);
  private MigrationRetryConfiguration retry = new MigrationRetryConfiguration();

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public Duration getImporterFinishedTimeout() {
    return importerFinishedTimeout;
  }

  public void setImporterFinishedTimeout(final Duration importerFinishedTimeout) {
    this.importerFinishedTimeout = importerFinishedTimeout;
  }

  public MigrationRetryConfiguration getRetry() {
    return retry;
  }

  public void setRetry(final MigrationRetryConfiguration retry) {
    this.retry = retry;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(final Duration timeout) {
    this.timeout = timeout;
  }

  public static class MigrationRetryConfiguration extends RetryConfiguration {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_MIN_RETRY_DELAY = Duration.ofSeconds(1);
    private static final Duration DEFAULT_MAX_RETRY_DELAY = Duration.ofMinutes(1);
    private static final double DEFAULT_MULTIPLIER = 2.0;

    @Override
    public int defaultMaxRetries() {
      return DEFAULT_MAX_RETRIES;
    }

    @Override
    public Duration defaultMinRetryDelay() {
      return DEFAULT_MIN_RETRY_DELAY;
    }

    @Override
    public Duration defaultMaxRetryDelay() {
      return DEFAULT_MAX_RETRY_DELAY;
    }

    @Override
    public double defaultRetryDelayMultiplier() {
      return DEFAULT_MULTIPLIER;
    }
  }
}
