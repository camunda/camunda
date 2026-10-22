/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import java.time.Duration;

/**
 * Configuration of the {@link FailureDetectorType#SWIM SWIM} failure detector.
 *
 * <p>All windows are whole seconds: they are operational time scales, set apart from the
 * milliseconds SWIM probes with. A region is failed over in two steps, like an incident is first
 * detected and then given time to recover: a broker that no observer has seen for the {@link
 * #getDetectionWindow() detection window} is gone, and a region of which every system partition
 * broker is gone is given the {@link #getRecoveryWindow() recovery window} to come back before the
 * leader fails over from it.
 */
public final class SwimCfg implements ConfigurationEntry {
  public static final Duration DEFAULT_REPORT_INTERVAL = Duration.ofSeconds(1);
  public static final Duration DEFAULT_REPORT_TIMEOUT = Duration.ofSeconds(5);
  public static final Duration DEFAULT_DETECTION_WINDOW = Duration.ofSeconds(30);
  public static final Duration DEFAULT_RECOVERY_WINDOW = Duration.ofSeconds(30);
  public static final int DEFAULT_MIN_OBSERVERS = 2;

  private Duration reportInterval = DEFAULT_REPORT_INTERVAL;
  private Duration reportTimeout = DEFAULT_REPORT_TIMEOUT;
  private Duration detectionWindow = DEFAULT_DETECTION_WINDOW;
  private Duration recoveryWindow = DEFAULT_RECOVERY_WINDOW;
  private int minObservers = DEFAULT_MIN_OBSERVERS;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    requireWholeSeconds("reportInterval", reportInterval);
    requireWholeSeconds("reportTimeout", reportTimeout);
    requireWholeSeconds("detectionWindow", detectionWindow);
    requireWholeSeconds("recoveryWindow", recoveryWindow);

    if (reportInterval.isZero() || reportInterval.isNegative()) {
      throw new IllegalArgumentException(
          "reportInterval %s must be at least 1s".formatted(reportInterval));
    }
    if (reportTimeout.compareTo(reportInterval.multipliedBy(2)) < 0) {
      throw new IllegalArgumentException(
          ("reportTimeout %s must be at least twice the reportInterval %s, or a single late report"
                  + " makes an observer silent")
              .formatted(reportTimeout, reportInterval));
    }
    if (detectionWindow.compareTo(reportTimeout) < 0) {
      throw new IllegalArgumentException(
          "detectionWindow %s must be at least the reportTimeout %s"
              .formatted(detectionWindow, reportTimeout));
    }
    final var swimFailureTimeout = globalConfig.getCluster().getMembership().getFailureTimeout();
    if (detectionWindow.compareTo(swimFailureTimeout) < 0) {
      throw new IllegalArgumentException(
          ("detectionWindow %s must be at least the SWIM failure timeout %s, or brokers are"
                  + " declared gone before SWIM itself gave up on them")
              .formatted(detectionWindow, swimFailureTimeout));
    }
    if (recoveryWindow.isNegative()) {
      throw new IllegalArgumentException(
          "recoveryWindow %s must not be negative".formatted(recoveryWindow));
    }
    if (minObservers < 1) {
      throw new IllegalArgumentException(
          "minObservers %d must be at least 1".formatted(minObservers));
    }
  }

  private static void requireWholeSeconds(final String name, final Duration duration) {
    if (duration.getNano() != 0) {
      throw new IllegalArgumentException(
          ("%s %s must be a whole number of seconds: the detector's windows are in seconds, unlike"
                  + " the milliseconds SWIM probes with")
              .formatted(name, duration));
    }
  }

  public Duration getReportInterval() {
    return reportInterval;
  }

  public void setReportInterval(final Duration reportInterval) {
    this.reportInterval = reportInterval;
  }

  public Duration getReportTimeout() {
    return reportTimeout;
  }

  public void setReportTimeout(final Duration reportTimeout) {
    this.reportTimeout = reportTimeout;
  }

  public Duration getDetectionWindow() {
    return detectionWindow;
  }

  public void setDetectionWindow(final Duration detectionWindow) {
    this.detectionWindow = detectionWindow;
  }

  public Duration getRecoveryWindow() {
    return recoveryWindow;
  }

  public void setRecoveryWindow(final Duration recoveryWindow) {
    this.recoveryWindow = recoveryWindow;
  }

  public int getMinObservers() {
    return minObservers;
  }

  public void setMinObservers(final int minObservers) {
    this.minObservers = minObservers;
  }

  @Override
  public String toString() {
    return "SwimCfg{"
        + "reportInterval="
        + reportInterval
        + ", reportTimeout="
        + reportTimeout
        + ", detectionWindow="
        + detectionWindow
        + ", recoveryWindow="
        + recoveryWindow
        + ", minObservers="
        + minObservers
        + '}';
  }
}
