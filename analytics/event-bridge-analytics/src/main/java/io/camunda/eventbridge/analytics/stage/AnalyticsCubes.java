/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The seeded set of standard cubes, expressed as {@link DatasetDeclaration}s — the declaration-
 * driven replacement for the hand-wired standard dashboards. Both stages call {@link #seed()} to
 * obtain identical {@link ActiveCube}s: the compiler resolves each declaration deterministically
 * against fresh registries, so compiling the same declarations in the same order yields the same
 * {@code cubeId}s and {@code aggId}s in both processes without any shared state.
 *
 * <p>Cubes are activated from the start of history (empty activation vector) — appropriate for the
 * dev/seed path. The control plane will supply per-partition activation watermarks when datasets
 * are provisioned at runtime.
 */
public final class AnalyticsCubes {

  private static final long ONE_MINUTE_MS = 60_000L;
  private static final long ONE_HOUR_MS = 3_600_000L;

  private AnalyticsCubes() {}

  /** Compiles the standard declarations into runnable cubes; deterministic and side-effect free. */
  public static List<ActiveCube> seed() {
    final DatasetCompiler compiler =
        new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterRegistry(new InMemoryMeterIdStore()));
    final DatasetRegistry registry = new DatasetRegistry();
    final List<ActiveCube> cubes = new ArrayList<>();
    for (final DatasetDeclaration declaration : declarations()) {
      final RegisteredDataset registered = registry.admit(declaration, Map.of());
      cubes.add(new ActiveCube(registered, compiler.compile(registered.cubeId(), declaration)));
    }
    return List.copyOf(cubes);
  }

  /** The standard dashboards as declarations (order is stable — it fixes cube/agg ids). */
  private static List<DatasetDeclaration> declarations() {
    return List.of(
        // Process-instance throughput + duration summary per process definition.
        DatasetDeclaration.builder("process-instances", FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("lifecycle", MeterCatalog.LIFECYCLE_SUMMARY, "durationMs"))
            .window(ONE_MINUTE_MS)
            .build(),
        // Completed-instance duration percentiles per process definition (mergeable sketch:
        // tiered).
        DatasetDeclaration.builder("process-duration", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.COMPLETED.name())
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("p95", MeterCatalog.PERCENTILE, "durationMs"))
            .window(ONE_MINUTE_MS)
            .window(ONE_HOUR_MS)
            .build(),
        // Flow-node execution counts per (process, element).
        DatasetDeclaration.builder("element-throughput", FactType.ELEMENT)
            .filterEquals("transition", Transition.COMPLETED.name())
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("elementId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(ONE_MINUTE_MS)
            .build(),
        // Incident counts per (process, element).
        DatasetDeclaration.builder("incidents", FactType.INCIDENT)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("elementId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(ONE_MINUTE_MS)
            .build());
  }
}
