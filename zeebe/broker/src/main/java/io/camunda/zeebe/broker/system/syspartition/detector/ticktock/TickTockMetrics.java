/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import io.camunda.zeebe.broker.system.syspartition.detector.ticktock.TickTockMetricsDoc.BrokerKeyNames;
import io.camunda.zeebe.broker.system.syspartition.detector.ticktock.TickTockMetricsDoc.RegionKeyNames;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** The metrics of the tick-tock failure detector, removed again when it closes. */
final class TickTockMetrics implements AutoCloseable {
  private final MeterRegistry meterRegistry;
  private final List<Meter> meters = new ArrayList<>();
  private final AtomicLong latestTick = new AtomicLong();
  private final Map<String, BrokerGauges> brokerGauges = new HashMap<>();
  private final Map<String, Counter> tocksReceived = new HashMap<>();
  private final Map<String, AtomicLong> regionStates = new HashMap<>();
  private final Map<String, AtomicLong> regionFailoverTicks = new HashMap<>();
  private final Map<String, Meter> regionFailoverGauges = new HashMap<>();
  private final Timer tickCommitLatency;

  TickTockMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    meters.add(
        Gauge.builder(TickTockMetricsDoc.TICK.getName(), latestTick, AtomicLong::get)
            .description(TickTockMetricsDoc.TICK.getDescription())
            .register(meterRegistry));
    tickCommitLatency =
        MicrometerUtil.buildTimer(TickTockMetricsDoc.TICK_COMMIT_LATENCY).register(meterRegistry);
    meters.add(tickCommitLatency);
  }

  void setLatestTick(final long tickId) {
    latestTick.set(tickId);
  }

  void tockReceived(final String broker) {
    tocksReceived
        .computeIfAbsent(
            broker,
            ignored -> {
              final var counter =
                  Counter.builder(TickTockMetricsDoc.TOCKS_RECEIVED.getName())
                      .description(TickTockMetricsDoc.TOCKS_RECEIVED.getDescription())
                      .tag(BrokerKeyNames.BROKER.asString(), broker)
                      .register(meterRegistry);
              meters.add(counter);
              return counter;
            })
        .increment();
  }

  void recordTickCommitLatency(final Duration latency) {
    tickCommitLatency.record(latency);
  }

  /** Reports how many ticks the broker missed and its liveness state code. */
  void setBrokerLiveness(final String broker, final long missedTicks, final int stateCode) {
    final var gauges = brokerGauges.computeIfAbsent(broker, this::registerBrokerGauges);
    gauges.missedTicks.set(missedTicks);
    gauges.stateCode.set(stateCode);
  }

  void setRegionState(final String region, final int stateCode) {
    regionStates
        .computeIfAbsent(
            region,
            ignored -> {
              final var state = new AtomicLong();
              meters.add(
                  Gauge.builder(TickTockMetricsDoc.REGION_STATE.getName(), state, AtomicLong::get)
                      .description(TickTockMetricsDoc.REGION_STATE.getDescription())
                      .tag(RegionKeyNames.REGION.asString(), region)
                      .register(meterRegistry));
              return state;
            })
        .set(stateCode);
  }

  /**
   * Reports the tick on which a failover from the failed to the target region was committed. Only
   * the latest failover is reported: a failover between other regions replaces the previous one.
   */
  void setRegionFailover(final String failedRegion, final String targetRegion, final long tickId) {
    final var key = failedRegion + "->" + targetRegion;
    if (!regionFailoverTicks.containsKey(key)) {
      regionFailoverGauges.values().forEach(this::remove);
      regionFailoverGauges.clear();
      regionFailoverTicks.clear();
      regionFailoverGauges.clear();
      final var tick = new AtomicLong();
      final var gauge =
          Gauge.builder(TickTockMetricsDoc.REGION_FAILOVER_TICK.getName(), tick, AtomicLong::get)
              .description(TickTockMetricsDoc.REGION_FAILOVER_TICK.getDescription())
              .tag(RegionKeyNames.REGION.asString(), failedRegion)
              .tag(RegionKeyNames.TARGET_REGION.asString(), targetRegion)
              .register(meterRegistry);
      meters.add(gauge);
      regionFailoverGauges.put(key, gauge);
      regionFailoverTicks.put(key, tick);
    }
    regionFailoverTicks.get(key).set(tickId);
  }

  private void remove(final Meter meter) {
    meterRegistry.remove(meter);
    meters.remove(meter);
  }

  @Override
  public void close() {
    meters.forEach(meterRegistry::remove);
    meters.clear();
    brokerGauges.clear();
    tocksReceived.clear();
    regionStates.clear();
    regionFailoverTicks.clear();
    regionFailoverGauges.clear();
  }

  private BrokerGauges registerBrokerGauges(final String broker) {
    final var gauges = new BrokerGauges();
    meters.add(
        Gauge.builder(
                TickTockMetricsDoc.MISSED_TICKS.getName(), gauges.missedTicks, AtomicLong::get)
            .description(TickTockMetricsDoc.MISSED_TICKS.getDescription())
            .tag(BrokerKeyNames.BROKER.asString(), broker)
            .register(meterRegistry));
    meters.add(
        Gauge.builder(TickTockMetricsDoc.BROKER_STATE.getName(), gauges.stateCode, AtomicLong::get)
            .description(TickTockMetricsDoc.BROKER_STATE.getDescription())
            .tag(BrokerKeyNames.BROKER.asString(), broker)
            .register(meterRegistry));
    return gauges;
  }

  private static final class BrokerGauges {
    private final AtomicLong missedTicks = new AtomicLong();
    private final AtomicLong stateCode = new AtomicLong();
  }
}
