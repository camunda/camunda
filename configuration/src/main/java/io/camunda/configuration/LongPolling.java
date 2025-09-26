/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults;

public class LongPolling {

  /** Enables long polling for available jobs */
  private boolean enabled = ConfigurationDefaults.DEFAULT_LONG_POLLING_ENABLED;

  /** Set the timeout for long polling in milliseconds */
  private long timeout = ConfigurationDefaults.DEFAULT_LONG_POLLING_TIMEOUT;

  /** Set the probe timeout for long polling in milliseconds */
  private long probeTimeout = ConfigurationDefaults.DEFAULT_PROBE_TIMEOUT;

  /**
   * Set the number of minimum empty responses, a minimum number of responses with jobCount of 0
   * infers that no jobs are available
   */
  private int minEmptyResponses =
      ConfigurationDefaults.DEFAULT_LONG_POLLING_EMPTY_RESPONSE_THRESHOLD;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(final long timeout) {
    this.timeout = timeout;
  }

  public long getProbeTimeout() {
    return probeTimeout;
  }

  public void setProbeTimeout(final long probeTimeout) {
    this.probeTimeout = probeTimeout;
  }

  public int getMinEmptyResponses() {
    return minEmptyResponses;
  }

  public void setMinEmptyResponses(final int minEmptyResponses) {
    this.minEmptyResponses = minEmptyResponses;
  }

  @Override
  public LongPolling clone() {
    final LongPolling copy = new LongPolling();
    copy.enabled = enabled;
    copy.timeout = timeout;
    copy.probeTimeout = probeTimeout;
    copy.minEmptyResponses = minEmptyResponses;

    return copy;
  }
}
