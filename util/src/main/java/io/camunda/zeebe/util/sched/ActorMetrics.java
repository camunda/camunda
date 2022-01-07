/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

public class ActorMetrics {

  private static final Histogram EXECUTION_LATENCY =
      Histogram.build()
          .namespace("zeebe")
          .name("actor_task_execution_latency")
          .help("Execution time of a certain actor task")
          .labelNames("actorName")
          .register();

  private static final Counter EXECUTION_COUNT =
      Counter.build()
          .namespace("zeebe")
          .name("actor_task_execution_count")
          .help("Number of times a certain actor task was executed")
          .labelNames("actorName")
          .register();

  public Histogram.Timer startExecutionTimer(final String name) {
    return EXECUTION_LATENCY.labels(name).startTimer();
  }

  public void countExecution(final String name) {
    EXECUTION_COUNT.labels(name).inc();
  }
}
