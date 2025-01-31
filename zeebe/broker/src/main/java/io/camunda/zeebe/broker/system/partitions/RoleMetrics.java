/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RoleMetrics {
  private final AtomicLong leaderTransitionLatency = new AtomicLong();
  private final Clock clock;

  public RoleMetrics(final MeterRegistry registry, final int partitionId) {
    clock = registry.config().clock();

    final var meterDoc = RoleMetricsDoc.LEADER_TRANSITION_LATENCY;
    TimeGauge.builder(
            meterDoc.getName(), leaderTransitionLatency, TimeUnit.MILLISECONDS, Number::longValue)
        .description(meterDoc.getDescription())
        .tag(PartitionKeyNames.PARTITION.asString(), String.valueOf(partitionId))
        .register(registry);
  }

  public CloseableSilently startLeaderTransitionLatencyTimer() {
    return MicrometerUtil.timer(leaderTransitionLatency::set, TimeUnit.MILLISECONDS, clock);
  }

  @SuppressWarnings("NullableProblems")
  public enum RoleMetricsDoc implements ExtendedMeterDocumentation {
    /**
     * The time (in ms) needed for the engine services to transition to leader and be ready to
     * process new requests.
     */
    LEADER_TRANSITION_LATENCY {
      @Override
      public String getDescription() {
        return "The time (in ms) needed for the engine services to transition to leader and be ready to process new requests";
      }

      @Override
      public String getName() {
        return "zeebe.leader.transition.latency";
      }

      @Override
      public Type getType() {
        return Meter.Type.GAUGE;
      }

      @Override
      public KeyName[] getKeyNames() {
        return PartitionKeyNames.values();
      }
    }
  }
}
