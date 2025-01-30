/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.prometheus.client.Gauge;
import io.prometheus.client.Gauge.Timer;

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

  public Timer createStartupTimer(final String stepName) {
    return STARTUP_METRIC.labels(stepName).startTimer();
  }

  public Timer createCloseTimer(final String stepName) {
    return CLOSE_METRICS.labels(stepName).startTimer();
  }
}
