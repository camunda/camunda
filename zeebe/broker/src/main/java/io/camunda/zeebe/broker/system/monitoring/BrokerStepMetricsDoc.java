/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter;

public enum BrokerStepMetricsDoc implements ExtendedMeterDocumentation {
  /** The time in milliseconds for each broker start step to complete */
  STARTUP {
    @Override
    public String getName() {
      return "zeebe.broker.start.step.latency";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public String getDescription() {
      return "The time in milliseconds for each broker start step to complete";
    }
  },

  /** The time in milliseconds for each broker close step to complete */
  CLOSE {
    @Override
    public String getName() {
      return "zeebe.broker.close.step.latency";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public String getDescription() {
      return "The time in milliseconds for each broker close step to complete";
    }
  }
}
