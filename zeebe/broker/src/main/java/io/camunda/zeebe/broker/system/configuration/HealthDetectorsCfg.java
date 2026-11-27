/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Prototype: health detectors and the health history. Disabled by default; with it disabled nothing
 * reports {@code DEGRADED} and no history is kept.
 *
 * <p>Each detector has defaults in code. {@link #getDetectors()} overrides them per detector, keyed
 * by the detector's name, for example {@code
 * zeebe.broker.experimental.healthDetectors.detectors.journalFlushLatency.threshold=200}. Keys are
 * matched ignoring case and dashes.
 */
@NullMarked
public final class HealthDetectorsCfg implements ConfigurationEntry {

  private boolean enabled = false;
  private Duration sampleInterval = Duration.ofSeconds(10);
  private int historySize = 10_000;
  private Map<String, DetectorCfg> detectors = new HashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getSampleInterval() {
    return sampleInterval;
  }

  public void setSampleInterval(final Duration sampleInterval) {
    this.sampleInterval = sampleInterval;
  }

  public int getHistorySize() {
    return historySize;
  }

  public void setHistorySize(final int historySize) {
    this.historySize = historySize;
  }

  public Map<String, DetectorCfg> getDetectors() {
    return detectors;
  }

  public void setDetectors(final Map<String, DetectorCfg> detectors) {
    this.detectors = detectors;
  }

  /** The overrides of one detector; every setting left out keeps the detector's default. */
  public DetectorCfg detector(final String name) {
    final var wanted = normalize(name);
    return detectors.entrySet().stream()
        .filter(entry -> normalize(entry.getKey()).equals(wanted))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseGet(DetectorCfg::new);
  }

  private static String normalize(final String name) {
    return name.replace("-", "").toLowerCase(Locale.ROOT);
  }

  @Override
  public String toString() {
    return "HealthDetectorsCfg{"
        + "enabled="
        + enabled
        + ", sampleInterval="
        + sampleInterval
        + ", historySize="
        + historySize
        + ", detectors="
        + detectors
        + '}';
  }

  /** Overrides of one detector; null keeps the detector's default. */
  public static final class DetectorCfg {

    private @Nullable Boolean enabled;
    private @Nullable Double threshold;
    private @Nullable Double clearThreshold;
    private @Nullable Duration minDuration;
    private @Nullable Duration clearDuration;

    public @Nullable Boolean getEnabled() {
      return enabled;
    }

    public void setEnabled(final @Nullable Boolean enabled) {
      this.enabled = enabled;
    }

    public @Nullable Double getThreshold() {
      return threshold;
    }

    public void setThreshold(final @Nullable Double threshold) {
      this.threshold = threshold;
    }

    public @Nullable Double getClearThreshold() {
      return clearThreshold;
    }

    public void setClearThreshold(final @Nullable Double clearThreshold) {
      this.clearThreshold = clearThreshold;
    }

    public @Nullable Duration getMinDuration() {
      return minDuration;
    }

    public void setMinDuration(final @Nullable Duration minDuration) {
      this.minDuration = minDuration;
    }

    public @Nullable Duration getClearDuration() {
      return clearDuration;
    }

    public void setClearDuration(final @Nullable Duration clearDuration) {
      this.clearDuration = clearDuration;
    }

    @Override
    public String toString() {
      return "DetectorCfg{"
          + "enabled="
          + enabled
          + ", threshold="
          + threshold
          + ", clearThreshold="
          + clearThreshold
          + ", minDuration="
          + minDuration
          + ", clearDuration="
          + clearDuration
          + '}';
    }
  }
}
