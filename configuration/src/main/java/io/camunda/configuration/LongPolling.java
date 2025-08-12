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
import java.util.Map;
import java.util.Set;

public class LongPolling {
  private static final String PREFIX = "camunda.api.long-polling";

  private static final Map<String, String> LEGACY_GATEWAY_PROPERTIES =
      Map.of(
          "enabled", "zeebe.gateway.longPolling.enabled",
          "timeout", "zeebe.gateway.longPolling.timeout",
          "probeTimeout", "zeebe.gateway.longPolling.probeTimeout",
          "minEmptyResponses", "zeebe.gateway.longPolling.minEmptyResponses");

  private static final Map<String, String> LEGACY_BROKER_PROPERTIES =
      Map.of(
          "enabled", "zeebe.broker.gateway.longPolling.enabled",
          "timeout", "zeebe.broker.gateway.longPolling.timeout",
          "probeTimeout", "zeebe.broker.gateway.longPolling.probeTimeout",
          "minEmptyResponses", "zeebe.broker.gateway.longPolling.minEmptyResponses");

  private Map<String, String> legacyPropertiesMap = LEGACY_BROKER_PROPERTIES;

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
        Set.of(legacyPropertiesMap.get("enabled")));
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
        Set.of(legacyPropertiesMap.get("timeout")));
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
        Set.of(legacyPropertiesMap.get("probeTimeout")));
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
        Set.of(legacyPropertiesMap.get("minEmptyResponses")));
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

  public LongPolling withBrokerLongPollingProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_BROKER_PROPERTIES;
    return copy;
  }

  public LongPolling withGatewayLongPollingProperties() {
    final var copy = clone();
    copy.legacyPropertiesMap = LEGACY_GATEWAY_PROPERTIES;
    return copy;
  }
}
