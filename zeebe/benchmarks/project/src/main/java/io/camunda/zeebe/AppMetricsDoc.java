/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter.Type;

/** Metrics shared across all app types (Starter and Worker). */
public enum AppMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * A gauge set to 1 when the client successfully connects to the gateway (i.e. after the first
   * successful topology request), and 0 otherwise. This metric is used by the verification workflow
   * to confirm that the client is connected, regardless of whether the client uses gRPC or REST.
   */
  CONNECTED {
    @Override
    public String getDescription() {
      return "Set to 1 when the client successfully connects to the gateway (topology received), 0 otherwise.";
    }

    @Override
    public String getName() {
      return "app.connected";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }
  };
}
