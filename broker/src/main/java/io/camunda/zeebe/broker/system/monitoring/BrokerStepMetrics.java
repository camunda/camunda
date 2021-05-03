/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.monitoring;

import io.prometheus.client.Gauge;

public class BrokerStepMetrics {

  public static final String ZEEBE_NAMESPACE = "zeebe";
  public static final String STEP_NAME_LABEL = "stepName";
  private static final Gauge STARTUP_METRIC =
      Gauge.build()
          .namespace(ZEEBE_NAMESPACE)
          .name("broker_start_step_latency")
          .help("Time for each broker start step to complete.")
          .labelNames(STEP_NAME_LABEL)
          .register();

  private static final Gauge CLOSE_METRICS =
      Gauge.build()
          .namespace(ZEEBE_NAMESPACE)
          .name("broker_close_step_latency")
          .help("Time for each broker close step to complete.")
          .labelNames(STEP_NAME_LABEL)
          .register();

  /**
   * Meter the time to start for a single step.
   *
   * @param stepName the name of the step
   * @param startupDuration the step start duration in ms
   */
  public void observeDurationForStarStep(String stepName, long startupDuration) {
    STARTUP_METRIC.labels(stepName).set(startupDuration);
  }

  /**
   * Meter the the time to close for a single step.
   *
   * @param stepName the name of the step
   * @param closeDuration the step close duration in ms
   */
  public void observeDurationForCloseStep(String stepName, long closeDuration) {
    CLOSE_METRICS.labels(stepName).set(closeDuration);
  }
}
