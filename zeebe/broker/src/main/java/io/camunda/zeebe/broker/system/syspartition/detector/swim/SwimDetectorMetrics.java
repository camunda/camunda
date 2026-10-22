/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import io.camunda.zeebe.broker.system.syspartition.detector.swim.SwimDetectorCore.Evaluation;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.SwimDetectorMetricsDoc.BrokerKeyNames;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.SwimDetectorMetricsDoc.RegionKeyNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** The metrics of the SWIM failure detector, removed again when it closes. */
final class SwimDetectorMetrics implements AutoCloseable {
  private final MeterRegistry meterRegistry;
  private final List<Meter> meters = new ArrayList<>();
  private final Map<String, BrokerGauges> brokerGauges = new HashMap<>();
  private final Map<String, AtomicLong> regionStates = new HashMap<>();
  private final Map<String, Meter> failoverGauges = new HashMap<>();
  private final AtomicLong freshReports = new AtomicLong();

  SwimDetectorMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    meters.add(
        Gauge.builder(SwimDetectorMetricsDoc.FRESH_REPORTS.getName(), freshReports, AtomicLong::get)
            .description(SwimDetectorMetricsDoc.FRESH_REPORTS.getDescription())
            .register(meterRegistry));
  }

  void update(final Evaluation evaluation) {
    freshReports.set(evaluation.freshReports());
    evaluation
        .views()
        .forEach(
            (broker, view) -> {
              final var gauges = brokerGauges.computeIfAbsent(broker, this::registerBrokerGauges);
              gauges.stateCode.set(view.state().code());
              gauges.goneMillis.set(view.goneForMillis());
            });
    evaluation
        .regionStates()
        .forEach(
            (region, state) ->
                regionStates.computeIfAbsent(region, this::registerRegionState).set(state.code()));
  }

  /**
   * Reports the time of the latest failover from the failed to the target region. Only the latest
   * failover is reported: a failover between other regions replaces the previous one.
   */
  void setFailover(
      final String failedRegion, final String targetRegion, final long decidedAtMillis) {
    final var key = failedRegion + "->" + targetRegion;
    if (!failoverGauges.containsKey(key)) {
      failoverGauges.values().forEach(this::remove);
      failoverGauges.clear();
    }
    final var epochSeconds = new AtomicLong(TimeUnit.MILLISECONDS.toSeconds(decidedAtMillis));
    failoverGauges.compute(
        key,
        (ignored, previous) -> {
          if (previous != null) {
            remove(previous);
          }
          final var gauge =
              Gauge.builder(
                      SwimDetectorMetricsDoc.REGION_FAILOVER.getName(),
                      epochSeconds,
                      AtomicLong::get)
                  .description(SwimDetectorMetricsDoc.REGION_FAILOVER.getDescription())
                  .tag(RegionKeyNames.REGION.asString(), failedRegion)
                  .tag(RegionKeyNames.TARGET_REGION.asString(), targetRegion)
                  .register(meterRegistry);
          meters.add(gauge);
          return gauge;
        });
  }

  private AtomicLong registerRegionState(final String region) {
    final var state = new AtomicLong();
    meters.add(
        Gauge.builder(SwimDetectorMetricsDoc.REGION_STATE.getName(), state, AtomicLong::get)
            .description(SwimDetectorMetricsDoc.REGION_STATE.getDescription())
            .tag(RegionKeyNames.REGION.asString(), region)
            .register(meterRegistry));
    return state;
  }

  private BrokerGauges registerBrokerGauges(final String broker) {
    final var gauges = new BrokerGauges();
    meters.add(
        Gauge.builder(
                SwimDetectorMetricsDoc.BROKER_STATE.getName(), gauges.stateCode, AtomicLong::get)
            .description(SwimDetectorMetricsDoc.BROKER_STATE.getDescription())
            .tag(BrokerKeyNames.BROKER.asString(), broker)
            .register(meterRegistry));
    meters.add(
        Gauge.builder(
                SwimDetectorMetricsDoc.BROKER_GONE_DURATION.getName(),
                gauges.goneMillis,
                millis -> millis.get() / 1000.0)
            .description(SwimDetectorMetricsDoc.BROKER_GONE_DURATION.getDescription())
            .tag(BrokerKeyNames.BROKER.asString(), broker)
            .register(meterRegistry));
    return gauges;
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
    regionStates.clear();
    failoverGauges.clear();
  }

  private static final class BrokerGauges {
    private final AtomicLong stateCode = new AtomicLong();
    private final AtomicLong goneMillis = new AtomicLong();
  }
}
