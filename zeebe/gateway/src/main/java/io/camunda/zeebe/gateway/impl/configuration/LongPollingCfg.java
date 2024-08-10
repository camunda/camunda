/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import java.util.Objects;

public final class LongPollingCfg {

  private boolean enabled = ConfigurationDefaults.DEFAULT_LONG_POLLING_ENABLED;
  private long timeout = ConfigurationDefaults.DEFAULT_LONG_POLLING_TIMEOUT;
  private long probeTimeout = ConfigurationDefaults.DEFAULT_PROBE_TIMEOUT;
  private int minEmptyResponses =
      ConfigurationDefaults.DEFAULT_LONG_POLLING_EMPTY_RESPONSE_THRESHOLD;

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

  public boolean isEnabled() {
    return enabled;
  }

  public LongPollingCfg setEnabled(final boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, timeout, probeTimeout, minEmptyResponses);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final LongPollingCfg that = (LongPollingCfg) o;
    return enabled == that.enabled
        && timeout == that.timeout
        && probeTimeout == that.probeTimeout
        && minEmptyResponses == that.minEmptyResponses;
  }

  @Override
  public String toString() {
    return "LongPollingCfg{"
        + "enabled="
        + enabled
        + ", timeout="
        + timeout
        + ", probeTimeout="
        + probeTimeout
        + ", minEmptyResponses="
        + minEmptyResponses
        + '}';
  }
}
