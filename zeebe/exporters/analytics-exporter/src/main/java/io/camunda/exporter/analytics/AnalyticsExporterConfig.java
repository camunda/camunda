/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.camunda.exporter.analytics.sampling.HashSampler;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configuration for the Analytics Exporter. Instantiated from the exporter's args map. */
public class AnalyticsExporterConfig {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyticsExporterConfig.class);

  private String endpoint = "https://analytics.cloud.camunda.io";
  private int maxQueueSize = 2048;
  private int maxBatchSize = 512;
  private String pushInterval = "PT5M";
  private String heartbeatInterval = "PT10M";
  private boolean signing = true;
  private boolean allowInsecure = false;
  private double samplingRate = HashSampler.MAX_SAMPLE_RATE;

  public String getEndpoint() {
    return endpoint;
  }

  public AnalyticsExporterConfig setEndpoint(final String endpoint) {
    this.endpoint = endpoint;
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

  public Duration getHeartbeatInterval() {
    return Duration.parse(heartbeatInterval);
  }

  public AnalyticsExporterConfig setHeartbeatInterval(final String heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
    return this;
  }

  public boolean isSigning() {
    return signing;
  }

  public AnalyticsExporterConfig setSigning(final boolean signing) {
    this.signing = signing;
    return this;
  }

  public boolean isAllowInsecure() {
    return allowInsecure;
  }

  public AnalyticsExporterConfig setAllowInsecure(final boolean allowInsecure) {
    this.allowInsecure = allowInsecure;
    return this;
  }

  public double getSamplingRate() {
    return samplingRate;
  }

  public AnalyticsExporterConfig setSamplingRate(final double samplingRate) {
    this.samplingRate = samplingRate;
    return this;
  }

  /** Validates configuration, logging warnings where appropriate. */
  public AnalyticsExporterConfig validate() {
    validateEndpoint();
    final Duration parsedPush;
    try {
      parsedPush = Duration.parse(pushInterval);
    } catch (final Exception e) {
      throw new IllegalArgumentException("Invalid pushInterval: " + pushInterval, e);
    }
    if (parsedPush.isZero() || parsedPush.isNegative()) {
      throw new IllegalArgumentException("pushInterval must be positive, got: " + pushInterval);
    }
    final Duration parsedHeartbeat;
    try {
      parsedHeartbeat = Duration.parse(heartbeatInterval);
    } catch (final Exception e) {
      throw new IllegalArgumentException("Invalid heartbeatInterval: " + heartbeatInterval, e);
    }
    if (parsedHeartbeat.isZero() || parsedHeartbeat.isNegative()) {
      throw new IllegalArgumentException(
          "heartbeatInterval must be positive, got: " + heartbeatInterval);
    }
    if (maxQueueSize <= 0) {
      throw new IllegalArgumentException("maxQueueSize must be positive, got: " + maxQueueSize);
    }
    if (maxBatchSize <= 0) {
      throw new IllegalArgumentException("maxBatchSize must be positive, got: " + maxBatchSize);
    }
    if (maxBatchSize > maxQueueSize) {
      throw new IllegalArgumentException(
          "maxBatchSize ("
              + maxBatchSize
              + ") must not exceed maxQueueSize ("
              + maxQueueSize
              + ")");
    }
    if (Double.isNaN(samplingRate)
        || samplingRate < HashSampler.MIN_SAMPLE_RATE
        || samplingRate > HashSampler.MAX_SAMPLE_RATE) {
      throw new IllegalArgumentException(
          "samplingRate must be between "
              + HashSampler.MIN_SAMPLE_RATE
              + " and "
              + HashSampler.MAX_SAMPLE_RATE
              + ", got: "
              + samplingRate);
    }
    return this;
  }

  private void validateEndpoint() {
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("Analytics exporter endpoint is not configured");
    }
    final URI uri = URI.create(endpoint);
    final String host = uri.getHost();
    final boolean isLocalhost =
        host != null
            && (host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.equals("[::1]")
                || host.equals("::1"));
    final String scheme = uri.getScheme();

    if ("https".equalsIgnoreCase(scheme)) {
      return;
    }

    if (!allowInsecure && !isLocalhost) {
      throw new IllegalArgumentException(
          "Analytics exporter endpoint must use https:// unless allowInsecure is true or the"
              + " host is localhost. Got: "
              + endpoint);
    }

    if (allowInsecure) {
      LOG.warn(
          "Analytics exporter endpoint uses an insecure scheme ({}). "
              + "This is allowed because allowInsecure=true, but is not recommended for"
              + " production.",
          scheme);
    }
  }

  /**
   * Returns a stable string encoding the config fields that affect which events are emitted and at
   * what rate. Used as input to the exporter digest.
   *
   * <p><b>Included</b> (behavioral — affect which events are emitted or what they contain): {@code
   * samplingRate}.
   *
   * <p><b>Excluded</b> (transport-only — affect delivery, not event semantics): {@code endpoint},
   * {@code maxQueueSize}, {@code maxBatchSize}, {@code pushInterval}, {@code heartbeatInterval},
   * {@code signing}, {@code allowInsecure}.
   *
   * <p>When adding a new field to this class, decide explicitly: if it changes event selection or
   * content, add it here; if it only affects delivery mechanics, leave it out and update the
   * excluded list above.
   */
  String toExporterDigestString() {
    return "samplingRate=" + samplingRate;
  }
}
