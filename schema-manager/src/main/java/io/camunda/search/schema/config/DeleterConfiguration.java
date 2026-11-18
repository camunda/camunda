/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.config;

public class DeleterConfiguration {
  // how many individual documents to delete in a single batch
  private int documentBatchSize = 2500;
  // how many root entities (e.g. process or decision instances) to read for deletion at once
  private int entityBatchSize = 500;
  private int maxRetryAttempts = 3;
  private int retryDelayMs = 1000;
  // delay in ms before the next run; grows up to maxDelayBetweenRuns while there is nothing to do
  private int delayBetweenRuns = 2000;
  private int maxDelayBetweenRuns = 60000;

  public int getEntityBatchSize() {
    return entityBatchSize;
  }

  public void setEntityBatchSize(final int entityBatchSize) {
    this.entityBatchSize = entityBatchSize;
  }

  public int getDocumentBatchSize() {
    return documentBatchSize;
  }

  public void setDocumentBatchSize(final int documentBatchSize) {
    this.documentBatchSize = documentBatchSize;
  }

  public int getMaxRetryAttempts() {
    return maxRetryAttempts;
  }

  public void setMaxRetryAttempts(final int maxRetryAttempts) {
    this.maxRetryAttempts = maxRetryAttempts;
  }

  public int getRetryDelayMs() {
    return retryDelayMs;
  }

  public void setRetryDelayMs(final int retryDelayMs) {
    this.retryDelayMs = retryDelayMs;
  }

  public int getDelayBetweenRuns() {
    return delayBetweenRuns;
  }

  public void setDelayBetweenRuns(final int delayBetweenRuns) {
    this.delayBetweenRuns = delayBetweenRuns;
  }

  public int getMaxDelayBetweenRuns() {
    return maxDelayBetweenRuns;
  }

  public void setMaxDelayBetweenRuns(final int maxDelayBetweenRuns) {
    this.maxDelayBetweenRuns = maxDelayBetweenRuns;
  }

  @Override
  public String toString() {
    return "DeleterConfiguration{"
        + "documentBatchSize="
        + documentBatchSize
        + ", entityBatchSize="
        + entityBatchSize
        + ", maxRetryAttempts="
        + maxRetryAttempts
        + ", retryDelayMs="
        + retryDelayMs
        + ", delayBetweenRuns="
        + delayBetweenRuns
        + ", maxDelayBetweenRuns="
        + maxDelayBetweenRuns
        + '}';
  }
}
