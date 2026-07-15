/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.catalog;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.ActiveTable;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetKind;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.serving.spi.DatasetSpecQuery;
import io.camunda.analytics.serving.spi.MetadataStore;
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
 *
 * <p><b>Catalog revision (breaking, supersedes the append-only rule once).</b> The catalog grew
 * three clusters of cubes differing only by meter on an identical grain — from before per-meter
 * filters and the matched-form ratio existed. This revision consolidates them (process-duration
 * absorbs process-duration-spread; process-quality absorbs process-sla, process-no-incident and
 * process-stp; elements absorbs element-throughput, element-duration and element-rework; incidents
 * absorbs incident-open; tenant-overview absorbs process-distinct and top-processes), which
 * re-mints every {@code cubeId}/{@code aggId}. Existing stores are incompatible and must be wiped
 * before deploying this revision; from here on the append-only rule applies again.
 */
public final class StandardDatasets {

  private static final long ONE_MINUTE_MS = 60_000L;
  private static final long ONE_HOUR_MS = 3_600_000L;
  // The completion-time SLA the sla_compliance cohort measures against (durationMs <= threshold).
  // Read from the {@code slaMs} property so the deployment picks it; the 9s default matches the
  // demo driver, whose deliberately-slow instances run 10-16s to breach it (a 5-minute default
  // would never be exceeded, showing a misleading 100%-met cohort).
  private static final long SLA_THRESHOLD_MS = Long.getLong("slaMs", 9_000L);

  /**
   * Allowed lateness (grace) for every windowed dataset. The merging aggregation drops a segment
   * delta once its window is {@code windowEnd + grace} behind the running event-time high-water,
   * and that high-water is global across all grouping keys — so with zero grace a fast key (or
   * simply the second delta for the same window) advances the water and every legitimately
   * in-flight delta for a slower key or the same window is dropped, collapsing the cube to almost
   * nothing. A few minutes of grace absorbs the cross-key spread and the shuffle/commit lag; open
   * windows are still served continuously via flush, they just finalize (and evict) this long after
   * their end.
   */
  private static final long GRACE_MS = 5 * ONE_MINUTE_MS;

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
            MeterCatalog.withDefaults(), new MeterIdRegistry(metadataStore.meterIdStore()));
    // The standard dashboards cover all retained history, so they activate from time 0.
    for (final DatasetDeclaration declaration : declarations()) {
      final RegisteredDataset registered = registry.admit(declaration, Map.of(), 0L);
      metadataStore.datasetSpecStore().create(registered);
      compiler.compile(registered.cubeId(), declaration); // side effect: allocate + persist aggIds
    }
    for (final DatasetDeclaration declaration : tableDeclarations()) {
      metadataStore.datasetSpecStore().create(registry.admit(declaration, Map.of(), 0L));
    }
  }

  /**
   * Loads the aggregated cubes from the metadata store and compiles them (ids come from the store).
   */
  public static List<ActiveCube> loadCubes(final MetadataStore metadataStore) {
    final DatasetCompiler compiler =
        new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterIdRegistry(metadataStore.meterIdStore()));
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
            MeterCatalog.withDefaults(), new MeterIdRegistry(metadataStore.meterIdStore()));
    final List<ActiveTable> tables = new ArrayList<>();
    for (final RegisteredDataset registered :
        metadataStore.datasetSpecStore().search(DatasetSpecQuery.byKind(DatasetKind.TABLE))) {
      tables.add(
          new ActiveTable(
              registered, compiler.compileTable(registered.cubeId(), registered.declaration())));
    }
    return List.copyOf(tables);
  }

  /**
   * The duration-band edges (ms) of the completion-time histogram the dashboard renders: five
   * bands, {@code [<10s, <30s, <60s, <120s, ≥120s]}.
   */
  private static final String DURATION_BAND_THRESHOLDS = "10000,30000,60000,120000";

  /** The standard dashboards as declarations (order is stable — it fixes cube/agg ids). */
  public static List<DatasetDeclaration> declarations() {
    return List.of(
        // Process-instance throughput + duration summary per process definition, composed from
        // primitive meters (per-meter filters) rather than the deprecated lifecycle_summary
        // bundle: one COUNT per transition, plus the duration family (count/avg/min/max and the
        // fixed completion-time bands) over the ended events. The duration meters skip the
        // duration-less ACTIVATED facts via the catalog's implicit NOT_NULL(measure) filter on
        // numeric-measure kinds — no explicit declaration needed. The plain duration meter stays
        // here (despite process-duration also carrying one) because the saved-report read composes
        // completed counts and duration stats from the SAME rows in one query.
        DatasetDeclaration.builder("process-instances", FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(
                Meter.of("activated", MeterCatalog.COUNT)
                    .filtered(FilterPredicate.equals(Fact.TRANSITION, Transition.ACTIVATED.name())))
            .meter(
                Meter.of("completed", MeterCatalog.COUNT)
                    .filtered(FilterPredicate.equals(Fact.TRANSITION, Transition.COMPLETED.name())))
            .meter(
                Meter.of("terminated", MeterCatalog.COUNT)
                    .filtered(
                        FilterPredicate.equals(Fact.TRANSITION, Transition.TERMINATED.name())))
            .meter(Meter.of("duration", MeterCatalog.EXECUTION_TIME, "durationMs"))
            .meter(
                new Meter(
                    "duration_bands",
                    MeterCatalog.HISTOGRAM,
                    "durationMs",
                    Map.of("thresholds", DURATION_BAND_THRESHOLDS)))
            .window(ONE_MINUTE_MS)
            .lateness(GRACE_MS)
            .build(),
        // Completed-instance duration distribution per process definition, consolidated onto one
        // COMPLETED-filtered grain (absorbs the former process-duration-spread): the percentile
        // sketch (default ranks p50/p75/p90/p99 — the dashboard's control chart and KPI tiles),
        // the additive execution-time summary (count/avg/min/max) and the population stddev (the
        // control-chart band). Percentiles are a mergeable sketch, so the cube is tiered.
        DatasetDeclaration.builder("process-duration", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.COMPLETED.name())
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("percentiles", MeterCatalog.PERCENTILE, "durationMs"))
            .meter(Meter.of("duration", MeterCatalog.EXECUTION_TIME, "durationMs"))
            .meter(Meter.of("stddev", MeterCatalog.STDDEV, "durationMs"))
            .window(ONE_MINUTE_MS)
            .window(ONE_HOUR_MS)
            .lateness(GRACE_MS)
            .build(),
        // Outcome-quality ratios per process definition, consolidated onto one unfiltered grain
        // (absorbs the former process-sla, process-no-incident and process-stp): each ratio scopes
        // its own population via a per-meter filter, so one cube serves three denominators.
        //  - sla_compliance: of COMPLETED instances, the share within the SLA (durationMs <=
        //    threshold) — completions only, mirroring the original process-sla population.
        //  - no_incident: of ENDED instances (transition != ACTIVATED — a faulted instance is
        //    cancelled and terminates, so restricting to COMPLETED would drop exactly the ones
        //    that had an incident and make the ratio a trivial 100%), the share that raised no
        //    incident (hadIncident == 0).
        //  - first_time_right: of ENDED instances, the matched-form conjunction "completed AND
        //    within the SLA AND incident-free". hadIncident compares EQUALS "false": ended facts
        //    ALWAYS carry the flag (the completion deriver reads it off the finalized row, whose
        //    BooleanProperty defaults to false), so the absent-field-never-matches-EQUALS trap
        //    does not apply here.
        DatasetDeclaration.builder("process-quality", FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(
                new Meter(
                        "sla_compliance",
                        MeterCatalog.RATIO,
                        "durationMs",
                        Map.of("op", "le", "threshold", Long.toString(SLA_THRESHOLD_MS)))
                    .filtered(FilterPredicate.equals(Fact.TRANSITION, Transition.COMPLETED.name())))
            .meter(
                new Meter(
                        "no_incident",
                        MeterCatalog.RATIO,
                        "hadIncident",
                        Map.of("op", "eq", "threshold", "0"))
                    .filtered(
                        FilterPredicate.notEquals(Fact.TRANSITION, Transition.ACTIVATED.name())))
            .meter(
                Meter.of("first_time_right", MeterCatalog.RATIO)
                    .filtered(
                        FilterPredicate.notEquals(Fact.TRANSITION, Transition.ACTIVATED.name()))
                    .matched(
                        FilterPredicate.equals(Fact.TRANSITION, Transition.COMPLETED.name()),
                        FilterPredicate.lessOrEqual("durationMs", Long.toString(SLA_THRESHOLD_MS)),
                        FilterPredicate.equals("hadIncident", "false")))
            .window(ONE_MINUTE_MS)
            .lateness(GRACE_MS)
            .build(),
        // Flow-node execution metrics per (process, element), consolidated onto one unfiltered
        // grain (absorbs the former element-throughput, element-duration and element-rework):
        // throughput and the duration family over completions, plus the rework pair over
        // activations. Rework per element is derived on read as max(0, activations − instances) —
        // exact while the HLL is exact (small counts; it stores hashes exactly up to its sketch
        // threshold), an approximation at scale, and never negative by construction of the read.
        // The duration meters' COMPLETED filter also keeps the duration-less ACTIVATED facts out
        // of their slots.
        DatasetDeclaration.builder("elements", FactType.ELEMENT)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("elementId", DimensionType.STRING)
            .meter(
                Meter.of("completed", MeterCatalog.COUNT)
                    .filtered(FilterPredicate.equals(Fact.TRANSITION, Transition.COMPLETED.name())))
            .meter(
                Meter.of("duration", MeterCatalog.EXECUTION_TIME, "durationMs")
                    .filtered(FilterPredicate.equals(Fact.TRANSITION, Transition.COMPLETED.name())))
            .meter(
                Meter.of("duration_p", MeterCatalog.PERCENTILE, "durationMs")
                    .filtered(FilterPredicate.equals(Fact.TRANSITION, Transition.COMPLETED.name())))
            .meter(
                Meter.of("activations", MeterCatalog.COUNT)
                    .filtered(FilterPredicate.equals(Fact.TRANSITION, Transition.ACTIVATED.name())))
            .meter(
                Meter.of("instances", MeterCatalog.DISTINCT, "processInstanceKey")
                    .filtered(FilterPredicate.equals(Fact.TRANSITION, Transition.ACTIVATED.name())))
            .window(ONE_MINUTE_MS)
            .window(ONE_HOUR_MS)
            .lateness(GRACE_MS)
            .build(),
        // Incident metrics per (process, element), consolidated (absorbs the former
        // incident-open): raised counts CREATED facts only (the incident fact stream also carries
        // RESOLVED facts — an unfiltered count would report raised + resolved), and the open
        // gauge is the running sum of the ±1 incident delta (CREATED +1 / RESOLVED −1), so a sum
        // over windows is the current open count.
        DatasetDeclaration.builder("incidents", FactType.INCIDENT)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("elementId", DimensionType.STRING)
            .meter(
                Meter.of("count", MeterCatalog.COUNT)
                    .filtered(FilterPredicate.equals(Fact.TRANSITION, Transition.CREATED.name())))
            .meter(Meter.of("open", MeterCatalog.LEVEL, "delta"))
            .window(ONE_MINUTE_MS)
            .lateness(GRACE_MS)
            .build(),
        // Tenant-level rollups, consolidated onto one grain (absorbs the former process-distinct
        // and top-processes): distinct active process definitions (HLL) and the heaviest
        // definitions (frequent-items). Both are mergeable sketches read hourly (the distinct
        // trend) or as range totals (the top-k ranking), so a single hourly tier serves both.
        DatasetDeclaration.builder("tenant-overview", FactType.PROCESS_INSTANCE)
            .dimension("tenantId", DimensionType.STRING)
            .meter(Meter.of("distinct", MeterCatalog.DISTINCT, "bpmnProcessId"))
            .meter(Meter.of("top", MeterCatalog.TOP_K, "bpmnProcessId"))
            .window(ONE_HOUR_MS)
            .lateness(GRACE_MS)
            .build(),
        // Completed-dispute counts and duration percentiles grouped by the process-set 'type'
        // variable (the bank-dispute classification) — the standard exerciser of the name-targeted
        // variable enrichment (grouping AND filtering by var.* fields) on the realistic load.
        // Load-run-specific: it only fills when the realistic-load driver's bank-dispute processes
        // run, and it stays in the standard catalog to keep that driver's variable path covered.
        // The filter must reference a ROOT-scoped variable: an output-mapping target lives (and
        // dies) in its element's flow scope, so a subprocess-mapped variable (e.g. this process's
        // isRefund) is never visible from the process-instance completion fact and an EQUALS
        // filter on it would silently reject every fact. customerId comes from the start payload,
        // which always lands at the root scope.
        DatasetDeclaration.builder("dispute-types", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.COMPLETED.name())
            .filterNotNull("var.customerId")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("var.type", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .meter(Meter.of("p95", MeterCatalog.PERCENTILE, "durationMs"))
            .window(ONE_MINUTE_MS)
            .lateness(GRACE_MS)
            .build(),
        // Currently-active instances per definition (a level gauge: the facts' ±1 lifecycle
        // delta), sampled as periodic snapshots — "how many were running at each moment", the
        // canonical semi-additive series (ADR 0010). APPENDED: declaration order fixes cube ids.
        // Deliberately tight lateness: a snapshot exists only once its boundary is provably
        // final, so the grace is the series' floor staleness — one minute keeps the widget ~2
        // minutes behind now instead of ~6, and the late-drop alarm makes any overrun visible.
        DatasetDeclaration.builder("active-instances", FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("active", MeterCatalog.LEVEL, "delta"))
            .window(ONE_MINUTE_MS)
            .lateness(ONE_MINUTE_MS)
            .snapshots(ONE_MINUTE_MS)
            .build(),
        // Completed-instance count + duration p95 segmented by the 'region' process variable (set
        // by the demo's region-exec-time-demo process) — the default "duration by segment" report
        // source. NOT_NULL(var.region) keeps region-less processes out of the cube. APPENDED.
        DatasetDeclaration.builder("region-duration", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.COMPLETED.name())
            .filterNotNull("var.region")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("var.region", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .meter(Meter.of("p95", MeterCatalog.PERCENTILE, "durationMs"))
            .window(ONE_MINUTE_MS)
            .lateness(GRACE_MS)
            .build(),
        // Business value processed per definition: the sum of the deriver-enriched 'value' field
        // (the designated value variable, default 'amount') over COMPLETED instances. The field is
        // an eager numeric enrichment — a var.* field rides facts as lazily-resolved TEXT, which
        // SUM cannot measure — and is absent on value-less processes, so the implicit
        // NOT_NULL(value) filter keeps them out instead of folding phantom zeroes. APPENDED.
        DatasetDeclaration.builder("value-throughput", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.COMPLETED.name())
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("processed", MeterCatalog.SUM, "value"))
            .window(ONE_MINUTE_MS)
            .lateness(GRACE_MS)
            .build(),
        // Business value currently in flight per definition, mirroring active-instances: a LEVEL
        // gauge over the signed 'valueDelta' enrichment (+value on ACTIVATED, −value on either
        // end), sampled as periodic snapshots with the same deliberately tight lateness (the
        // snapshot-freshness trade-off documented on active-instances). The engine materializes
        // the ACTIVATION-time value on the instance row and stamps exactly that on both sides, so
        // the level stays balanced even when the variable appears or changes mid-flight (such an
        // instance contributes nothing).
        // APPENDED.
        DatasetDeclaration.builder("value-in-flight", FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("value", MeterCatalog.LEVEL, "valueDelta"))
            .window(ONE_MINUTE_MS)
            .lateness(ONE_MINUTE_MS)
            .snapshots(ONE_MINUTE_MS)
            .build(),
        // Execution variants per definition: instance counts and duration percentiles on the
        // (bpmnProcessId, variantHash) grain. variantHash is the deriver's order-insensitive
        // commutative signature over the instance's distinct executed elements with bucketed loop
        // counts (see the engine's VariantSignature) — a LONG dimension, so grouping needs no
        // string key. Only end facts carry it (NOT_NULL keeps ACTIVATED facts out), and both
        // COMPLETED and TERMINATED count: a mid-flight termination is its own partial-set
        // variant. Tiered like process-duration — the top-variants read is a range total.
        // APPENDED.
        DatasetDeclaration.builder("process-variants", FactType.PROCESS_INSTANCE)
            .filterNotNull("variantHash")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("variantHash", DimensionType.LONG)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            // Explicit ranks: the variants card compares variants at p50/p95, and a sketch result
            // answers only the ranks it was declared with (valueAt on any other rank is NaN).
            .meter(
                new Meter(
                    "duration_p",
                    MeterCatalog.PERCENTILE,
                    "durationMs",
                    Map.of("ranks", "0.5,0.95")))
            .window(ONE_MINUTE_MS)
            .window(ONE_HOUR_MS)
            .lateness(GRACE_MS)
            .build(),
        // Completed-instance count + duration percentiles grouped by the 'route' process variable —
        // a correlation cube for the outlier-analysis feature (task #27): "which variable values
        // are
        // over-represented among duration outliers" needs a per-value duration sketch to answer, on
        // top of the overall fence process-duration's 'percentiles' meter already exposes.
        // Correlation cubes are discovered by naming convention rather than a registry: a catalog
        // dataset named 'corr-*' with exactly one 'var.*' dimension is a duration-correlation cube
        // (see the read side's CorrelationCubes classifier). Only root-scope (start-payload)
        // variables are visible to PROCESS_INSTANCE facts, like dispute-types. GUARDRAIL
        // (documented,
        // not enforced): keep per-variable cardinality low — this cube is one row per distinct
        // value,
        // not per instance. APPENDED.
        DatasetDeclaration.builder("corr-route", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.COMPLETED.name())
            .filterNotNull("var.route")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("var.route", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .meter(
                new Meter(
                    "duration_p",
                    MeterCatalog.PERCENTILE,
                    "durationMs",
                    Map.of("ranks", "0.25,0.5,0.75,0.95")))
            .window(ONE_MINUTE_MS)
            .lateness(GRACE_MS)
            .build(),
        // Same shape as corr-route, grouped by 'region' instead. APPENDED.
        DatasetDeclaration.builder("corr-region", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.COMPLETED.name())
            .filterNotNull("var.region")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("var.region", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .meter(
                new Meter(
                    "duration_p",
                    MeterCatalog.PERCENTILE,
                    "durationMs",
                    Map.of("ranks", "0.25,0.5,0.75,0.95")))
            .window(ONE_MINUTE_MS)
            .lateness(GRACE_MS)
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
            .build(),
        // The live working set of open instances (aging WIP): one row per running instance,
        // inserted on activation and evicted on any other transition (completed OR terminated).
        // startTime is the activation event time (the same field the completion facts carry), so
        // the dashboard derives each row's age as now − startTime; processInstanceKey doubles as
        // a column because a fetched TableRow carries only its declared columns, not its row key.
        // APPENDED: declaration order is stable here too.
        DatasetDeclaration.builder("open-instances", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.ACTIVATED.name())
            .asTable("processInstanceKey")
            .evictWhen(FilterPredicate.notEquals(Fact.TRANSITION, Transition.ACTIVATED.name()))
            .dimension("processInstanceKey", DimensionType.LONG)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("startTime", DimensionType.LONG)
            .build(),
        // The variant dictionary: one row per observed variantHash carrying its human-readable
        // canonical element list (the hash's display companion — the cube stores only the LONG).
        // Upserted from every end fact that carries a variant; idempotent by key, and every
        // instance of a variant writes the identical row, so replays and races are harmless (the
        // signature folds the bpmnProcessId in as a seed, so the hash-only key cannot collide
        // across processes with identical element-id sets).
        // Tiny by construction: one row per distinct variant, not per instance. variantHash
        // doubles as a column because a fetched TableRow carries only its declared columns.
        // APPENDED.
        DatasetDeclaration.builder("variant-catalog", FactType.PROCESS_INSTANCE)
            .filterNotNull("variantHash")
            .asTable("variantHash")
            .dimension("variantHash", DimensionType.LONG)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .dimension("variantElements", DimensionType.TEXT)
            .build());
  }
}
