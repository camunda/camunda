/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camunda.migration.process")
public class ProcessMigrationProperties {

  private int batchSize = 20;
  private int maxRetries = 3;
  private Duration minRetryDelay = Duration.ofSeconds(1);
  private Duration maxRetryDelay = Duration.ofMinutes(1);
  private Duration postImporterTimeout = Duration.ofMinutes(1);

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(final int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public Duration getMinRetryDelay() {
    return minRetryDelay;
  }

  public void setMinRetryDelay(final Duration minRetryDelay) {
    this.minRetryDelay = minRetryDelay;
  }

  public Duration getMaxRetryDelay() {
    return maxRetryDelay;
  }

  public void setMaxRetryDelay(final Duration maxRetryDelay) {
    this.maxRetryDelay = maxRetryDelay;
  }

  public Duration getPostImporterTimeout() {
    return postImporterTimeout;
  }

  public void setPostImporterTimeout(final Duration postImporterTimeout) {
    this.postImporterTimeout = postImporterTimeout;
  }
}
