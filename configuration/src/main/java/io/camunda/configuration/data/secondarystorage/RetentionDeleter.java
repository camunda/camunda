/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.secondarystorage;

import java.time.Duration;

public class RetentionDeleter {

  /** how many documents are deleted in a single bulk delete request */
  private int documentBatchSize = 2500;

  /**
   * how many entities (e.g. process instances or decision instances) are read at once when finding
   * data whose retention period has expired
   */
  private int entityBatchSize = 500;

  /** how many times a failed delete is retried before giving up */
  private int maxRetryAttempts = 3;

  /** how long to wait before retrying a failed delete */
  private Duration retryDelay = Duration.ofSeconds(1);

  public int getDocumentBatchSize() {
    return documentBatchSize;
  }

  public void setDocumentBatchSize(final int documentBatchSize) {
    this.documentBatchSize = documentBatchSize;
  }

  public int getEntityBatchSize() {
    return entityBatchSize;
  }

  public void setEntityBatchSize(final int entityBatchSize) {
    this.entityBatchSize = entityBatchSize;
  }

  public int getMaxRetryAttempts() {
    return maxRetryAttempts;
  }

  public void setMaxRetryAttempts(final int maxRetryAttempts) {
    this.maxRetryAttempts = maxRetryAttempts;
  }

  public Duration getRetryDelay() {
    return retryDelay;
  }

  public void setRetryDelay(final Duration retryDelay) {
    this.retryDelay = retryDelay;
  }
}
