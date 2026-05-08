/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter.Type;

public enum StarterCounterMetricsDoc implements ExtendedMeterDocumentation {

  /**
   * Total number of process instance start requests submitted by the starter. Incremented after the
   * request to create an instance has been dispatched, regardless of the eventual response — so
   * this is a measure of "instances we asked the engine to start", not "instances the engine
   * created". Used by the quicker load test to compute throughput at the end of a finite run.
   */
  PROCESS_INSTANCES_STARTED {
    @Override
    public String getDescription() {
      return "Total number of process instance start requests submitted by the starter.";
    }

    @Override
    public String getName() {
      return "starter.process.instances.started";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /**
   * Set to 1 when the starter has finished its instance-creation loop (either because the
   * configured duration-limit elapsed or because it was otherwise stopped). Stays at 0 while the
   * starter is actively creating instances. Lets external watchers (e.g. the quicker load test
   * workflow) detect completion without relying on pod phase — Spring Boot's WebFlux server keeps
   * the JVM alive after the CommandLineRunner returns, so the pod stays Running.
   */
  RUN_FINISHED {
    @Override
    public String getDescription() {
      return "1 once the starter has finished its instance-creation loop, 0 otherwise.";
    }

    @Override
    public String getName() {
      return "starter.run.finished";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  }
}
