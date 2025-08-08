/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults;
import java.util.Set;

public class LongPolling {
  private static final String PREFIX = "camunda.api.long-polling";

  private static final Set<String> LEGACY_ENABLED_PROPERTIES =
      Set.of("zeebe.gateway.longPolling.enabled");
  private static final Set<String> LEGACY_TIMEOUT_PROPERTIES =
      Set.of("zeebe.gateway.longPolling.timeout");
  private static final Set<String> LEGACY_PROBE_TIMEOUT_PROPERTIES =
      Set.of("zeebe.gateway.longPolling.probeTimeout");
  private static final Set<String> LEGACY_MIN_EMPTY_RESPONSES_PROPERTIES =
      Set.of("zeebe.gateway.longPolling.minEmptyResponses");

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
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enabled",
        enabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENABLED_PROPERTIES);
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public long getTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".timeout",
        timeout,
        Long.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_TIMEOUT_PROPERTIES);
  }

  public void setTimeout(final long timeout) {
    this.timeout = timeout;
  }

  public long getProbeTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".probe-timeout",
        probeTimeout,
        Long.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_PROBE_TIMEOUT_PROPERTIES);
  }

  public void setProbeTimeout(final long probeTimeout) {
    this.probeTimeout = probeTimeout;
  }

  public int getMinEmptyResponses() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".min-empty-responses",
        minEmptyResponses,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MIN_EMPTY_RESPONSES_PROPERTIES);
  }

  public void setMinEmptyResponses(final int minEmptyResponses) {
    this.minEmptyResponses = minEmptyResponses;
  }
}
