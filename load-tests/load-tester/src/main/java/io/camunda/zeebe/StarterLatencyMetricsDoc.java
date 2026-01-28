/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

public enum StarterLatencyMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * The data availability latency when starting process instances. It measures the time from
   * instance creation to the time the instance can be queried.
   */
  DATA_AVAILABILITY_LATENCY {
    private static final KeyName[] KEY_NAMES = new KeyName[] {StarterMetricKeyNames.PARTITION};

    private static final Duration[] BUCKETS = {
      Duration.ofMillis(500),
      Duration.ofSeconds(1),
      Duration.ofMillis(2500),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30),
      Duration.ofSeconds(45),
      Duration.ofSeconds(60),
      Duration.ofSeconds(90),
    };

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "The data availability latency when starting process instances. It measures the time from instance creation to the time the instance can be queried.";
    }

    @Override
    public String getName() {
      return "starter.data.availability.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }
  },

  /**
   * The response latency when starting process instances. It measures the time from sending the
   * request to receiving the response.
   */
  RESPONSE_LATENCY {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(10),
      Duration.ofMillis(25),
      Duration.ofMillis(50),
      Duration.ofMillis(75),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(2500),
      Duration.ofSeconds(5)
    };

    @Override
    public String getDescription() {
      return "The response latency when starting process instances. It measures the time from sending the request to receiving the response.";
    }

    @Override
    public String getName() {
      return "starter.response.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }
  };

  public enum StarterMetricKeyNames implements KeyName {

    /** The ID of the partition associated to the metric */
    PARTITION {
      @Override
      public String asString() {
        return "partition";
      }
    },
  }
}
