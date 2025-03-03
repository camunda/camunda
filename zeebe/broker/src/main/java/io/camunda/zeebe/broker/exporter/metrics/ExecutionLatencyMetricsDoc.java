/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import io.camunda.zeebe.broker.exporter.stream.ExporterMetricsDoc.ExporterContainerKeyNames;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

@SuppressWarnings("NullableProblems")
public enum ExecutionLatencyMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * The current cached entities for counting their execution latency. If only short-lived instances
   * are handled this can be seen or observed as the current active instance count.
   */
  CACHED_INSTANCES {
    @Override
    public String getDescription() {
      return """
        The current cached entities for counting their execution latency. If only \
        short-lived instances are handled this can be seen or observed as the current active \
        instance count.""";
    }

    @Override
    public String getName() {
      return "zeebe.execution.latency.current.cached.instances";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getKeyNames() {
      return CacheKeyNames.values();
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return KeyName.merge(PartitionKeyNames.values(), ExporterContainerKeyNames.values());
    }
  },

  /** The execution time of processing a complete process instance */
  PROCESS_INSTANCE_EXECUTION {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(50),
      Duration.ofMillis(75),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(2500),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30),
      Duration.ofSeconds(45),
      Duration.ofMinutes(1)
    };

    @Override
    public String getDescription() {
      return "The execution time of processing a complete process instance";
    }

    @Override
    public String getName() {
      return "zeebe.process.instance.execution.time";
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

  /** The lifetime of a job */
  JOB_LIFETIME {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(50),
      Duration.ofMillis(75),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(2500),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30),
      Duration.ofSeconds(45)
    };

    @Override
    public String getDescription() {
      return "The lifetime of a job";
    }

    @Override
    public String getName() {
      return "zeebe.job.life.time";
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

  /** The time until a job was activated */
  JOB_ACTIVATION_TIME {
    private static final Duration[] BUCKETS = {
      Duration.ofMillis(50),
      Duration.ofMillis(75),
      Duration.ofMillis(100),
      Duration.ofMillis(250),
      Duration.ofMillis(500),
      Duration.ofMillis(750),
      Duration.ofSeconds(1),
      Duration.ofMillis(2500),
      Duration.ofSeconds(5),
      Duration.ofSeconds(10),
      Duration.ofSeconds(15),
      Duration.ofSeconds(30)
    };

    @Override
    public String getDescription() {
      return "The time until a job was activated";
    }

    @Override
    public String getName() {
      return "zeebe.job.activation.time";
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

  public enum CacheKeyNames implements KeyName {
    /** The type of entities stored in the cache; see {@link CacheType} for possible values */
    TYPE {
      @Override
      public String asString() {
        return "type";
      }
    }
  }

  public enum CacheType {
    JOBS("jobs"),
    PROCESS_INSTANCES("processInstances");

    private final String tagValue;

    CacheType(final String tagValue) {
      this.tagValue = tagValue;
    }

    public String getTagValue() {
      return tagValue;
    }
  }
}
