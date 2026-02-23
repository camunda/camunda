/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.config;

public class Config {

  // Connection settings
  private String url;
  private String apiKey;

  // Error handling settings
  private boolean continueOnError = true;
  private int maxRetries = 2;
  private long retryDelayMs = 1500;
  private long requestTimeoutMs = 5000;

  // Batch settings
  private int batchSize = 50;
  private Long batchIntervalMs = 2000L;
  private int maxBatchesInFlight = 2;

  public String getUrl() {
    return url;
  }

  public Config setUrl(final String url) {
    this.url = url;
    return this;
  }

  public String getApiKey() {
    return apiKey;
  }

  public Config setApiKey(final String apiKey) {
    this.apiKey = apiKey;
    return this;
  }

  public boolean isContinueOnError() {
    return continueOnError;
  }

  public Config setContinueOnError(final boolean continueOnError) {
    this.continueOnError = continueOnError;
    return this;
  }

  public int getMaxBatchesInFlight() {
    return maxBatchesInFlight;
  }

  public Config setMaxBatchesInFlight(final int maxBatchesInFlight) {
    this.maxBatchesInFlight = maxBatchesInFlight;
    return this;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public Config setMaxRetries(final int maxRetries) {
    this.maxRetries = maxRetries;
    return this;
  }

  public long getRetryDelayMs() {
    return retryDelayMs;
  }

  public Config setRetryDelayMs(final long retryDelayMs) {
    this.retryDelayMs = retryDelayMs;
    return this;
  }

  public long getRequestTimeoutMs() {
    return requestTimeoutMs;
  }

  public Config setRequestTimeoutMs(final long requestTimeoutMs) {
    this.requestTimeoutMs = requestTimeoutMs;
    return this;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public Config setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
    return this;
  }

  public Long getBatchIntervalMs() {
    return batchIntervalMs;
  }

  public Config setBatchIntervalMs(final Long batchIntervalMs) {
    this.batchIntervalMs = batchIntervalMs;
    return this;
  }
}
