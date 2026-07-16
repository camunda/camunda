/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.projection.ProjectionMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The base projection's correctness signals as Micrometer counters, one set per source partition.
 * {@code analytics.projection.duplicate.skipped} is expected traffic (exporter retries absorbed by
 * the pre-fold watermark, ADR 0007); {@code fold.row.missing} and {@code fact.dropped} must stay at
 * zero — see {@link ProjectionMetrics}. Per cube it additionally exposes the gate counters ({@code
 * analytics.projection.cube.facts.inspected}/{@code .folded}) and the silent-empty-cube alarm gauge
 * ({@code analytics.projection.cube.silent} — 1 while a cube has inspected many admitted facts and
 * folded none). {@code analytics.facts.emitted} counts the base projection's fan-out into the fact
 * dispatcher, one pre-resolved counter per {@link FactType} so the hot path only ever looks up an
 * existing meter, never registers one.
 */
public final class MicrometerProjectionMetrics implements ProjectionMetrics {

  private final MeterRegistry registry;
  private final String partitionTag;
  private final Counter duplicateSkipped;
  private final Counter foldRowMissing;
  private final Counter factDropped;
  private final Map<FactType, Counter> factsEmitted = new EnumMap<>(FactType.class);
  // Lazily created per dataset, on the single owner (actor) thread that wires and warns cubes —
  // never touched concurrently, so a plain HashMap is safe.
  private final Map<String, Counter> emptyAlarms = new HashMap<>();

  public MicrometerProjectionMetrics(final MeterRegistry registry, final int partition) {
    this.registry = registry;
    partitionTag = String.valueOf(partition);
    duplicateSkipped =
        Counter.builder("analytics.projection.duplicate.skipped")
            .description(
                "Producer duplicates absorbed by the pre-fold watermark — expected under exporter retries")
            .tag("partition", partitionTag)
            .register(registry);
    foldRowMissing =
        Counter.builder("analytics.projection.fold.row.missing")
            .description("Must stay zero; a fold met a missing row")
            .tag("partition", partitionTag)
            .register(registry);
    factDropped =
        Counter.builder("analytics.projection.fact.dropped")
            .description("Must stay zero; a derivation lost its fact")
            .tag("partition", partitionTag)
            .register(registry);
    for (final FactType factType : FactType.values()) {
      factsEmitted.put(
          factType,
          Counter.builder("analytics.facts.emitted")
              .description("Facts the base projection emitted into the dispatch fan-out")
              .tag("partition", partitionTag)
              .tag("factType", factType.name())
              .register(registry));
    }
  }

  @Override
  public void duplicateSkipped() {
    duplicateSkipped.increment();
  }

  @Override
  public void foldRowMissing() {
    foldRowMissing.increment();
  }

  @Override
  public void factDropped() {
    factDropped.increment();
  }

  @Override
  public void factEmitted(final FactType factType) {
    factsEmitted.get(factType).increment();
  }

  @Override
  public void datasetEmptyAlarm(final String datasetName) {
    emptyAlarms
        .computeIfAbsent(
            datasetName,
            name ->
                Counter.builder("analytics.dataset.empty.alarm")
                    .description(
                        "The silent-empty-cube alarm fired: many admitted facts inspected, none"
                            + " folded")
                    .tag("dataset", name)
                    .register(registry))
        .increment();
  }

  /**
   * Racy-read exposure over the cube's single-writer gate counters. Registered once per constructed
   * wiring; a cube that is removed and re-added on a live reload keeps its first registration (the
   * counters simply stop moving), which is fine for advisory meters.
   */
  @Override
  public void registerCubeGate(final CubeGateStats stats) {
    FunctionCounter.builder(
            "analytics.projection.cube.facts.inspected", stats, CubeGateStats::factsInspected)
        .description("Type-matched, activation-admitted facts the cube's gate inspected")
        .tag("partition", partitionTag)
        .tag("dataset", stats.datasetName())
        .register(registry);
    FunctionCounter.builder(
            "analytics.projection.cube.facts.folded", stats, CubeGateStats::factsFolded)
        .description("Inspected facts that passed the declared filters and were folded")
        .tag("partition", partitionTag)
        .tag("dataset", stats.datasetName())
        .register(registry);
    Gauge.builder("analytics.projection.cube.silent", stats, s -> s.silent() ? 1 : 0)
        .description(
            "1 while the cube has inspected many admitted facts and folded none — its declared"
                + " filters match nothing")
        .tag("partition", partitionTag)
        .tag("dataset", stats.datasetName())
        .register(registry);
  }
}
