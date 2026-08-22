/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the zeebe-secrets benchmark driver, which drives {@code POST
 * /v2/secrets/resolve} and {@code POST /v2/secrets/list} against the gateway to benchmark the
 * secret-resolution API.
 *
 * <p>The scenario is shaped entirely from these knobs so the same driver image covers cache-first,
 * store-miss, batch-deduplication and concurrency-scaling runs by changing configuration alone:
 *
 * <ul>
 *   <li>{@code resolveRatio} splits traffic between the two endpoints.
 *   <li>{@code batchSize} and {@code duplicateRatio} shape each resolve batch, so the server-side
 *       deduplication effect can be measured.
 *   <li>{@code referencePoolSize} bounds how many distinct references a run touches: a small pool
 *       relative to the secret cache stays cache-first, a large pool forces store reads.
 *   <li>{@code warmup} pre-resolves the whole pool once before the measured load starts, so a
 *       cache-first run does not pay first-touch store cost in its recorded latencies.
 * </ul>
 */
public class ZeebeSecretsDriverProperties {

  /**
   * The largest batch the resolve endpoint accepts, mirrored from {@code
   * SecretRequestValidator.MAX_BATCH_SIZE}. A configured {@code batchSize} above this is clamped so
   * the driver never sends a batch the gateway would reject outright, which would measure
   * validation rejection latency rather than resolution.
   */
  public static final int MAX_BATCH_SIZE = 20;

  private boolean enabled = false;
  private double rate = 200;
  private Duration rateDuration = Duration.ofSeconds(1);
  private int threads = 2;
  private int durationLimit = 0;

  private double resolveRatio = 0.5;
  private int batchSize = 20;
  private double duplicateRatio = 0.3;
  private int referencePoolSize = 100;
  private String referencePrefix = "camunda.secrets.";
  private String referenceBaseName = "bench_";
  private Duration requestTimeout = Duration.ofSeconds(20);
  private boolean warmup = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public double getRate() {
    return rate;
  }

  public void setRate(final double rate) {
    this.rate = rate;
  }

  public Duration getRateDuration() {
    return rateDuration;
  }

  public void setRateDuration(final Duration rateDuration) {
    this.rateDuration = rateDuration;
  }

  public double getRatePerSecond() {
    return rate / (rateDuration.toNanos() / 1_000_000_000.0);
  }

  public int getThreads() {
    return threads;
  }

  public void setThreads(final int threads) {
    this.threads = threads;
  }

  public int getDurationLimit() {
    return durationLimit;
  }

  public void setDurationLimit(final int durationLimit) {
    this.durationLimit = durationLimit;
  }

  public double getResolveRatio() {
    return resolveRatio;
  }

  public void setResolveRatio(final double resolveRatio) {
    this.resolveRatio = resolveRatio;
  }

  /**
   * The number of references per resolve batch, clamped to {@code [1, MAX_BATCH_SIZE]} and to the
   * reference pool size, so the driver never sends an empty batch, a batch the gateway rejects, or
   * more distinct references than the pool holds.
   */
  public int getEffectiveBatchSize() {
    return Math.max(1, Math.min(batchSize, Math.min(MAX_BATCH_SIZE, referencePoolSize)));
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public double getDuplicateRatio() {
    return duplicateRatio;
  }

  public void setDuplicateRatio(final double duplicateRatio) {
    this.duplicateRatio = duplicateRatio;
  }

  public int getReferencePoolSize() {
    return referencePoolSize;
  }

  public void setReferencePoolSize(final int referencePoolSize) {
    this.referencePoolSize = referencePoolSize;
  }

  public String getReferencePrefix() {
    return referencePrefix;
  }

  public void setReferencePrefix(final String referencePrefix) {
    this.referencePrefix = referencePrefix;
  }

  public String getReferenceBaseName() {
    return referenceBaseName;
  }

  public void setReferenceBaseName(final String referenceBaseName) {
    this.referenceBaseName = referenceBaseName;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public boolean isWarmup() {
    return warmup;
  }

  public void setWarmup(final boolean warmup) {
    this.warmup = warmup;
  }

  /**
   * Builds the full pool of distinct references this run may touch, as {@code
   * <prefix><baseName><index>} for each index in {@code [0, referencePoolSize)}. The names match
   * the {@code camunda.secrets.<name>} contract the gateway validates, so a run against a file
   * store simply needs one mounted secret file per generated name.
   */
  public List<String> buildReferencePool() {
    final List<String> pool = new ArrayList<>(Math.max(0, referencePoolSize));
    for (int i = 0; i < referencePoolSize; i++) {
      pool.add(referencePrefix + referenceBaseName + i);
    }
    return pool;
  }
}
