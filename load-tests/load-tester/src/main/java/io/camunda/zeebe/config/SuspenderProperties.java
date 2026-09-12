/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import java.time.Duration;

/**
 * Configuration for the {@code suspender} load-test role, which periodically suspends and later
 * resumes running process instances to stress the suspend/resume feature on a real cluster.
 *
 * <p>The master switch is {@link #enabled}: leaving it {@code false} is the A/B baseline arm (no
 * suspend/resume traffic), setting it {@code true} is the suspend/resume arm.
 */
public class SuspenderProperties {

  /** Suspend/resume driver: per-instance commands, or process-instance batch operations. */
  public enum Mode {
    SINGLE,
    BATCH
  }

  private boolean enabled = false;
  private Mode mode = Mode.SINGLE;
  private String processId = "benchmark";

  // single mode: suspend attempts per rate-duration
  private double rate = 10;
  private Duration rateDuration = Duration.ofSeconds(1);
  private int sampleSize = 100;

  // batch mode
  private Duration batchInterval = Duration.ofSeconds(10);
  private int batchPageSize = 1000;

  /** How long an instance stays suspended before it is resumed. */
  private Duration holdDuration = Duration.ofSeconds(30);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Mode getMode() {
    return mode;
  }

  public void setMode(final Mode mode) {
    this.mode = mode;
  }

  public String getProcessId() {
    return processId;
  }

  public void setProcessId(final String processId) {
    this.processId = processId;
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

  public int getSampleSize() {
    return sampleSize;
  }

  public void setSampleSize(final int sampleSize) {
    this.sampleSize = sampleSize;
  }

  public Duration getBatchInterval() {
    return batchInterval;
  }

  public void setBatchInterval(final Duration batchInterval) {
    this.batchInterval = batchInterval;
  }

  public int getBatchPageSize() {
    return batchPageSize;
  }

  public void setBatchPageSize(final int batchPageSize) {
    this.batchPageSize = batchPageSize;
  }

  public Duration getHoldDuration() {
    return holdDuration;
  }

  public void setHoldDuration(final Duration holdDuration) {
    this.holdDuration = holdDuration;
  }
}
