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
   * The data availability query duration. It measures the individual time for each search query
   * which we use to track the data availability of a process instance creation.
   */
  DATA_AVAILABILITY_QUERY_DURATION {

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
    public String getDescription() {
      return "The data availability query duration. It measures the individual time for each search query, which we use to track the data availability of a process instance creation.";
    }

    @Override
    public String getName() {
      return "starter.data.availability.query.duration";
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
  },

  /**
   * A counter that is incremented once when the client successfully connects to the gateway (i.e.
   * after the first successful topology request). This metric is used by the verification workflow
   * to confirm that the client is connected, regardless of whether the client uses gRPC or REST.
   */
  CONNECTED {
    @Override
    public String getDescription() {
      return "Incremented once when the client successfully connects to the gateway (topology received).";
    }

    @Override
    public String getName() {
      return "app.connected";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }
  },

  /** The latency of read benchmark queries executed against the Camunda cluster. */
  READ_BENCHMARK {
    private static final KeyName[] KEY_NAMES = new KeyName[] {StarterMetricKeyNames.QUERY_NAME};

    private static final Duration[] BUCKETS = {
      Duration.ofMillis(10),
      Duration.ofMillis(15),
      Duration.ofMillis(25),
      Duration.ofMillis(50),
      Duration.ofMillis(75),
      Duration.ofMillis(100),
      Duration.ofMillis(150),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofMillis(1000),
      Duration.ofMillis(1500),
      Duration.ofMillis(2500),
      Duration.ofMillis(5000),
      Duration.ofMillis(10000),
      Duration.ofMillis(15000)
    };

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "The latency of read benchmark queries executed against the Camunda cluster.";
    }

    @Override
    public String getName() {
      return "starter.read.benchmark";
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

    /** The name of the query being benchmarked */
    QUERY_NAME {
      @Override
      public String asString() {
        return "query";
      }
    },
  }
}
