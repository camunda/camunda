/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.stage;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.ActiveProjection;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetKind;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dataset.store.DatasetSpecQuery;
import io.camunda.analytics.dataset.store.MetadataStore;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The standard dashboards as {@link DatasetDeclaration}s and the metadata-plane load path. The
 * database is the source of truth for which datasets exist: {@link #bootstrap} writes the standard
 * declarations once (allocating stable {@code cubeId}s and, by compiling, persisting {@code
 * aggId}s), and both stages call {@link #loadCubes}/{@link #loadProjections} to reload the specs
 * and compile them. Because the {@code aggId}s are pre-allocated at bootstrap and reloaded from the
 * store, both stages see identical ids without racing to allocate.
 *
 * <p>Declarations are seeded with an empty activation vector (from the start of history) — the
 * control plane will supply per-partition activation watermarks when datasets are provisioned at
 * runtime.
 */
public final class AnalyticsCubes {

  private static final long ONE_MINUTE_MS = 60_000L;
  private static final long ONE_HOUR_MS = 3_600_000L;
  private static final long SLA_THRESHOLD_MS = 300_000L;

  private AnalyticsCubes() {}

  /**
   * Writes the standard declarations into the metadata store if it is empty (idempotent bootstrap).
   * Allocates a stable {@code cubeId} per dataset and, by compiling the aggregated cubes, allocates
   * and persists their {@code aggId}s — so the stages later reload ids rather than racing to mint
   * them. A single control-plane invocation; concurrent first-run bootstraps are rejected by the
   * unique dataset name.
   */
  public static void bootstrap(final MetadataStore metadataStore) {
    if (!metadataStore.datasetSpecStore().isEmpty()) {
      return;
    }
    final DatasetRegistry registry = new DatasetRegistry();
    final DatasetCompiler compiler =
        new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterRegistry(metadataStore.meterIdStore()));
    for (final DatasetDeclaration declaration : declarations()) {
      final RegisteredDataset registered = registry.admit(declaration, Map.of());
      metadataStore.datasetSpecStore().create(registered);
      compiler.compile(registered.cubeId(), declaration); // side effect: allocate + persist aggIds
    }
    for (final DatasetDeclaration declaration : projectionDeclarations()) {
      metadataStore.datasetSpecStore().create(registry.admit(declaration, Map.of()));
    }
  }

  /**
   * Loads the aggregated cubes from the metadata store and compiles them (ids come from the store).
   */
  public static List<ActiveCube> loadCubes(final MetadataStore metadataStore) {
    final DatasetCompiler compiler =
        new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterRegistry(metadataStore.meterIdStore()));
    final List<ActiveCube> cubes = new ArrayList<>();
    for (final RegisteredDataset registered :
        metadataStore.datasetSpecStore().search(DatasetSpecQuery.byKind(DatasetKind.AGGREGATED))) {
      cubes.add(
          new ActiveCube(
              registered, compiler.compile(registered.cubeId(), registered.declaration())));
    }
    return List.copyOf(cubes);
  }

  /** Loads the projected (raw) datasets from the metadata store and compiles them. */
  public static List<ActiveProjection> loadProjections(final MetadataStore metadataStore) {
    final DatasetCompiler compiler =
        new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterRegistry(metadataStore.meterIdStore()));
    final List<ActiveProjection> projections = new ArrayList<>();
    for (final RegisteredDataset registered :
        metadataStore.datasetSpecStore().search(DatasetSpecQuery.byKind(DatasetKind.PROJECTED))) {
      projections.add(
          new ActiveProjection(
              registered,
              compiler.compileProjection(registered.cubeId(), registered.declaration())));
    }
    return List.copyOf(projections);
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
        // SLA-compliant share of completed instances per process definition (a ratio cohort).
        DatasetDeclaration.builder("process-sla", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.COMPLETED.name())
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(
                new Meter(
                    "sla_compliance",
                    MeterCatalog.RATIO,
                    "durationMs",
                    Map.of("op", "le", "threshold", Long.toString(SLA_THRESHOLD_MS))))
            .window(ONE_MINUTE_MS)
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

  /** The seeded projected (raw) datasets: flat, keyed rows rather than windowed aggregates. */
  private static List<DatasetDeclaration> projectionDeclarations() {
    return List.of(
        // Raw completed process instances, enriched with the region variable, keyed by instance.
        DatasetDeclaration.builder("raw-completed-instances", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.COMPLETED.name())
            .projectedBy("processInstanceKey")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("durationMs", DimensionType.LONG)
            .dimension("hadIncident", DimensionType.BOOLEAN)
            .dimension("var.region", DimensionType.STRING)
            .build());
  }
}
