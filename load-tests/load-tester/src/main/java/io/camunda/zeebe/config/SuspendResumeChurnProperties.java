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
 * Quick-and-dirty config for driving the process instance suspend/resume POC (#56552) during a load
 * test (track (c) of the benchmark plan) — not a reviewed feature, disabled by default.
 */
public class SuspendResumeChurnProperties {

  private boolean enabled = false;
  private Duration interval = Duration.ofSeconds(30);
  private int batchSize = 10;
  private Duration resumeDelay = Duration.ofSeconds(60);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getInterval() {
    return interval;
  }

  public void setInterval(final Duration interval) {
    this.interval = interval;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public Duration getResumeDelay() {
    return resumeDelay;
  }

  public void setResumeDelay(final Duration resumeDelay) {
    this.resumeDelay = resumeDelay;
  }
}
