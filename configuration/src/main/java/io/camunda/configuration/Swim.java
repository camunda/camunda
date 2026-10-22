/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;

/**
 * The SWIM failure detector: every system partition broker tracks the cluster membership from SWIM
 * gossip and periodically reports its view to the other system partition brokers. The system
 * partition leader consolidates the reports, and commits a region failover when a data region is
 * gone.
 *
 * <p>All windows are whole seconds, apart from the milliseconds SWIM itself probes with. A failover
 * has two steps, like an incident is first detected and then given time to recover: the detection
 * window decides when a broker is gone, and the recovery window gives a region of which every
 * system partition broker is gone the time to come back before the leader fails over from it.
 */
public class Swim {

  /** How often a broker reports its view of the cluster membership to the other brokers. */
  private Duration reportInterval = Duration.ofSeconds(1);

  /**
   * How long after its last report a broker's view is ignored. Must be at least twice the report
   * interval, so a single late report does not make a broker silent.
   */
  private Duration reportTimeout = Duration.ofSeconds(5);

  /**
   * The detection window: how long every observing broker must continuously have had a broker out
   * of sight before it is considered gone. Must be at least SWIM's failure timeout, so brokers are
   * not declared gone before SWIM itself gave up on them, and long enough to ride out SWIM's false
   * suspicions.
   */
  private Duration detectionWindow = Duration.ofSeconds(30);

  /**
   * The recovery window: how long a region of which every system partition broker was detected as
   * gone has to come back before the leader fails over from it. A region seen again within it is
   * not failed over. Zero fails over as soon as the region is detected as gone.
   */
  private Duration recoveryWindow = Duration.ofSeconds(30);

  /**
   * How many distinct brokers must have a broker out of sight before it is considered gone, so one
   * slow or isolated observer cannot condemn a broker alone.
   */
  private int minObservers = 2;

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
    return "Swim{"
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
