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
  }
}
