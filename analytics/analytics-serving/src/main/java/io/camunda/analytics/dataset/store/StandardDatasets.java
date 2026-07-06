/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.ActiveTable;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetKind;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dataset.RegisteredDataset;
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
 * The standard dashboard datasets as {@link DatasetDeclaration}s plus the metadata-plane
 * bootstrap/load path — the single source of truth shared by the pipeline (which computes these
 * cubes) and the serving/read layer (which reads whatever the metadata plane holds). The database
 * is authoritative: {@link #bootstrap} writes the declarations once (allocating stable {@code
 * cubeId}s and, by compiling, persisting {@code aggId}s), and {@link #loadCubes}/{@link
 * #loadTables} reload and compile them so every reader sees identical ids without racing to mint
 * them.
 *
 * <p>Declaration order is stable — it fixes the {@code cubeId}/{@code aggId} sequence — so new
 * datasets are only ever <em>appended</em>. Declarations start from the beginning of history (an
 * empty activation vector); the control plane supplies per-partition activation watermarks when
 * datasets are provisioned at runtime.
 */
public final class StandardDatasets {

  private static final long ONE_MINUTE_MS = 60_000L;
  private static final long ONE_HOUR_MS = 3_600_000L;
  private static final long SLA_THRESHOLD_MS = 300_000L;

  private StandardDatasets() {}

  /**
   * Writes the standard declarations into the metadata store if it is empty (idempotent bootstrap).
   * Allocates a stable {@code cubeId} per dataset and, by compiling the aggregated cubes, allocates
   * and persists their {@code aggId}s — so readers later reload ids rather than racing to mint
   * them. Concurrent first-run bootstraps are rejected by the unique dataset name.
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
    for (final DatasetDeclaration declaration : tableDeclarations()) {
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
  public static List<ActiveTable> loadTables(final MetadataStore metadataStore) {
    final DatasetCompiler compiler =
        new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterRegistry(metadataStore.meterIdStore()));
    final List<ActiveTable> tables = new ArrayList<>();
    for (final RegisteredDataset registered :
        metadataStore.datasetSpecStore().search(DatasetSpecQuery.byKind(DatasetKind.TABLE))) {
      tables.add(
          new ActiveTable(
              registered, compiler.compileTable(registered.cubeId(), registered.declaration())));
    }
    return List.copyOf(tables);
  }

  /** The standard dashboards as declarations (order is stable — it fixes cube/agg ids). */
  public static List<DatasetDeclaration> declarations() {
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
            .build(),
        // --- appended for the dashboard read layer (stable ids: never reorder above) ---
        // Distinct active process definitions per tenant (HLL, tiered hourly).
        DatasetDeclaration.builder("process-distinct", FactType.PROCESS_INSTANCE)
            .dimension("tenantId", DimensionType.STRING)
            .meter(Meter.of("distinct", MeterCatalog.DISTINCT, "bpmnProcessId"))
            .window(ONE_HOUR_MS)
            .build(),
        // Heaviest process definitions per tenant (frequent-items, tiered).
        DatasetDeclaration.builder("top-processes", FactType.PROCESS_INSTANCE)
            .dimension("tenantId", DimensionType.STRING)
            .meter(Meter.of("top", MeterCatalog.TOP_K, "bpmnProcessId"))
            .window(ONE_MINUTE_MS)
            .window(ONE_HOUR_MS)
            .build(),
        // Completed flow-node duration summary per (process, element): count/avg/max + percentiles.
        DatasetDeclaration.builder("element-duration", FactType.ELEMENT)
            .filterEquals("transition", Transition.COMPLETED.name())
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("elementId", DimensionType.STRING)
            .meter(Meter.of("duration", MeterCatalog.EXECUTION_TIME_SUMMARY, "durationMs"))
            .window(ONE_MINUTE_MS)
            .window(ONE_HOUR_MS)
            .build(),
        // Open-incident gauge per (process, element): running sum of the ±1 incident delta
        // (CREATED +1 / RESOLVED −1), so a sum over windows is the current open count.
        DatasetDeclaration.builder("incident-open", FactType.INCIDENT)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("elementId", DimensionType.STRING)
            .meter(Meter.of("open", MeterCatalog.LEVEL, "delta"))
            .window(ONE_MINUTE_MS)
            .build());
  }

  /** The projected (raw) datasets: flat, keyed rows rather than windowed aggregates. */
  public static List<DatasetDeclaration> tableDeclarations() {
    return List.of(
        // Raw completed process instances, enriched with the region variable, keyed by instance.
        DatasetDeclaration.builder("raw-completed-instances", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.COMPLETED.name())
            .asTable("processInstanceKey")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("durationMs", DimensionType.LONG)
            .dimension("hadIncident", DimensionType.BOOLEAN)
            .dimension("var.region", DimensionType.STRING)
            .build());
  }
}
