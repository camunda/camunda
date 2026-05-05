/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import java.time.Duration;

/** Configuration for the Analytics Exporter. Instantiated from the exporter's args map. */
public class AnalyticsExporterConfig {

  private String endpoint = "https://analytics.cloud.camunda.io";
  private boolean enabled = false;
  private int maxQueueSize = 2048;
  private int maxBatchSize = 512;
  private String pushInterval = "PT5S";

  public String getEndpoint() {
    return endpoint;
  }

  public AnalyticsExporterConfig setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
    return this;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public AnalyticsExporterConfig setEnabled(final boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public int getMaxQueueSize() {
    return maxQueueSize;
  }

  public AnalyticsExporterConfig setMaxQueueSize(final int maxQueueSize) {
    this.maxQueueSize = maxQueueSize;
    return this;
  }

  public int getMaxBatchSize() {
    return maxBatchSize;
  }

  public AnalyticsExporterConfig setMaxBatchSize(final int maxBatchSize) {
    this.maxBatchSize = maxBatchSize;
    return this;
  }

  public Duration getPushInterval() {
    return Duration.parse(pushInterval);
  }

  public AnalyticsExporterConfig setPushInterval(final String pushInterval) {
    this.pushInterval = pushInterval;
    return this;
  }

  public void validate() {
    if (enabled && (endpoint == null || endpoint.isBlank())) {
      throw new IllegalArgumentException(
          "Analytics exporter is enabled but no endpoint is configured");
    }
  }
}
