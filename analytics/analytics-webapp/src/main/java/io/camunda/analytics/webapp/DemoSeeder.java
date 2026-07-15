/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTier;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.CompositeAggregateFunction;
import io.camunda.analytics.query.DatasetQueryExecutor;
import io.camunda.analytics.query.ReportQuery;
import io.camunda.analytics.serving.spi.DatasetStore;
import io.camunda.analytics.serving.spi.VersionedDatasetWriter;
import io.camunda.analytics.serving.spi.WriteVersion;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds the serving store with representative demo data so the dashboard renders without a running
 * pipeline. Enabled only with {@code -Danalytics.seed=true}; a no-op if data already exists, so it
 * never touches a live dataset. It writes exactly what the pipeline's sinks write: for each cube,
 * per grain key and window, it folds synthetic {@link Fact}s through the cube's declared meter and
 * upserts the encoded accumulator via the neutral {@link DatasetStore#writer()} — the same
 * store/codecs the read path decodes.
 */
@Component
@ConditionalOnProperty(name = "analytics.seed", havingValue = "true")
public class DemoSeeder implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DemoSeeder.class);

  private static final String TENANT = "<default>";
  private static final int WINDOWS = 10;
  private static final long BASE_MS = 1_750_000_000_000L; // fixed start (deterministic demo)

  private static final List<String> PROCESSES =
      List.of("order-process", "payment-process", "shipping-process");
  private static final List<String> ELEMENTS =
      List.of("StartEvent_1", "Task_Validate", "Task_Approve", "Gateway_Check", "EndEvent_1");
  // per-tenant occurrence volume per window, busiest first (matches PROCESSES order)
  private static final long[] VOLUMES = {30L, 15L, 5L};

  private final DatasetStore store;
  private final DatasetCatalog catalog;
  private final DatasetQueryExecutor executor;

  // One writer for the whole seeding run: a writer batches its writes until flush() (the store
  // hands ownership of a fresh writer to each caller), so all seeds must share this instance.
  private VersionedDatasetWriter writer;

  public DemoSeeder(
      final DatasetStore store, final DatasetCatalog catalog, final DatasetQueryExecutor executor) {
    this.store = store;
    this.catalog = catalog;
    this.executor = executor;
  }

  @Override
  public void run(final String... args) {
    if (alreadySeeded()) {
      LOG.info("Demo seed skipped: the serving store already has process-instance data");
      return;
    }
    LOG.info("Seeding demo analytics data into the serving store");
    writer = store.writer();
    ensureSchemas();
    seedProcessInstances();
    seedProcessDuration();
    seedProcessQuality();
    seedElementCubes();
    seedTenantOverview();
    writer.flush();
    LOG.info("Demo analytics data seeded");
  }

  private boolean alreadySeeded() {
    final CompiledDataset dataset = catalog.require("process-instances");
    final long toMs = System.currentTimeMillis() + 3_600_000L;
    // Single total bucket: toMs rounded up to the finest tier, which the planner requires the
    // granularity to divide by.
    final long finestMs = dataset.finestTier().windowMs();
    final long granularityMs = ((toMs + finestMs - 1) / finestMs) * finestMs;
    final ReportQuery query =
        new ReportQuery(
            List.of("bpmnProcessId"), 0L, toMs, granularityMs, List.of(), List.of("activated"));
    return !executor.execute(query, dataset).rows().isEmpty();
  }

  private void ensureSchemas() {
    for (final CompiledDataset dataset : catalog.byName().values()) {
      store.schemaManager().ensure(dataset);
    }
  }

  /** process-instances (lifecycle): activated/completed/terminated + completion durations. */
  private void seedProcessInstances() {
    final CompiledDataset dataset = catalog.require("process-instances");
    for (final CompiledTier tier : dataset.tiers()) {
      for (final String process : PROCESSES) {
        final DimensionKey key = DimensionKey.of(dataset.grain(), process);
        for (int w = 0; w < WINDOWS; w++) {
          final long ws = windowStart(tier.windowMs(), w);
          final long count = instanceCount(process, w);
          final long completed = Math.round(count * 0.9);
          final List<Fact> facts = new ArrayList<>();
          for (long i = 0; i < count; i++) {
            facts.add(processFact(Transition.ACTIVATED, null));
          }
          for (final long duration : durations(baseP50(process), (int) completed)) {
            facts.add(processFact(Transition.COMPLETED, duration));
          }
          for (long i = 0; i < count - completed; i++) {
            facts.add(processFact(Transition.TERMINATED, null));
          }
          upsert(dataset, tier, key, ws, facts);
        }
      }
    }
  }

  /** process-duration: completion-duration percentiles, execution-time summary and stddev. */
  private void seedProcessDuration() {
    final CompiledDataset dataset = catalog.require("process-duration");
    for (final CompiledTier tier : dataset.tiers()) {
      for (final String process : PROCESSES) {
        final DimensionKey key = DimensionKey.of(dataset.grain(), process);
        for (int w = 0; w < WINDOWS; w++) {
          upsert(dataset, tier, key, windowStart(tier.windowMs(), w), completedFacts(process, w));
        }
      }
    }
  }

  /** process-quality: the SLA / no-incident / first-time-right ratios over incident-free ends. */
  private void seedProcessQuality() {
    final CompiledDataset dataset = catalog.require("process-quality");
    for (final CompiledTier tier : dataset.tiers()) {
      for (final String process : PROCESSES) {
        final DimensionKey key = DimensionKey.of(dataset.grain(), process);
        for (int w = 0; w < WINDOWS; w++) {
          upsert(dataset, tier, key, windowStart(tier.windowMs(), w), completedFacts(process, w));
        }
      }
    }
  }

  /** elements (throughput + durations) and incidents (raised + open) per (process, element). */
  private void seedElementCubes() {
    final CompiledDataset elements = catalog.require("elements");
    final CompiledDataset incidents = catalog.require("incidents");

    for (final String process : PROCESSES) {
      for (int e = 0; e < ELEMENTS.size(); e++) {
        final String element = ELEMENTS.get(e);
        for (int w = 0; w < WINDOWS; w++) {
          final long executed = 40L - e * 6L + (w % 4) * 2L;
          final long elementP50 = baseP50(process) / 5 + e * 3_000L;
          final long raised = (e == 1 || e == 2) ? 2L + (w % 3) : 0L;
          final long open = raised > 0 ? 1L : 0L;

          for (final CompiledTier tier : elements.tiers()) {
            final List<Fact> facts = new ArrayList<>();
            for (final long d : durations(elementP50, (int) Math.max(1, executed))) {
              facts.add(elementFact(Transition.COMPLETED, d));
            }
            upsert(
                elements,
                tier,
                DimensionKey.of(elements.grain(), process, element),
                windowStart(tier.windowMs(), w),
                facts);
          }
          for (final CompiledTier tier : incidents.tiers()) {
            // Raised incidents are CREATED (+1) facts; all but the open one resolve (−1), so the
            // CREATED-filtered count reads `raised` and the level gauge reads `open`.
            final List<Fact> facts = new ArrayList<>();
            for (long i = 0; i < raised; i++) {
              facts.add(deltaFact(Transition.CREATED, 1L));
            }
            for (long i = 0; i < raised - open; i++) {
              facts.add(deltaFact(Transition.RESOLVED, -1L));
            }
            upsert(
                incidents,
                tier,
                DimensionKey.of(incidents.grain(), process, element),
                windowStart(tier.windowMs(), w),
                facts);
          }
        }
      }
    }
  }

  /** tenant-overview: distinct definitions (HLL) + heaviest definitions (frequent items). */
  private void seedTenantOverview() {
    final CompiledDataset dataset = catalog.require("tenant-overview");
    for (final CompiledTier tier : dataset.tiers()) {
      final DimensionKey key = DimensionKey.of(dataset.grain(), TENANT);
      for (int w = 0; w < WINDOWS; w++) {
        final List<Fact> facts = new ArrayList<>();
        for (int p = 0; p < PROCESSES.size(); p++) {
          for (long i = 0; i < VOLUMES[p]; i++) {
            facts.add(
                Fact.builder(FactType.PROCESS_INSTANCE)
                    .field("bpmnProcessId", PROCESSES.get(p))
                    .build());
          }
        }
        upsert(dataset, tier, key, windowStart(tier.windowMs(), w), facts);
      }
    }
  }

  // --- fact builders -------------------------------------------------------------------------

  /** Incident-free completions (ended facts always carry the {@code hadIncident} flag). */
  private List<Fact> completedFacts(final String process, final int w) {
    final long completed = Math.round(instanceCount(process, w) * 0.9);
    final List<Fact> facts = new ArrayList<>();
    for (final long duration : durations(baseP50(process), (int) completed)) {
      facts.add(
          Fact.builder(FactType.PROCESS_INSTANCE)
              .transition(Transition.COMPLETED)
              .field("durationMs", duration)
              .field("hadIncident", false)
              .build());
    }
    return facts;
  }

  private static Fact processFact(final Transition transition, final Long durationMs) {
    return Fact.builder(FactType.PROCESS_INSTANCE)
        .transition(transition)
        .field("durationMs", durationMs)
        .build();
  }

  private static Fact elementFact(final Transition transition, final Long durationMs) {
    return Fact.builder(FactType.ELEMENT)
        .transition(transition)
        .field("durationMs", durationMs)
        .build();
  }

  private static Fact deltaFact(final Transition transition, final long delta) {
    return Fact.builder(FactType.INCIDENT).transition(transition).field("delta", delta).build();
  }

  /** A spread of durations around {@code baseP50} so percentiles have shape. */
  private static List<Long> durations(final long baseP50, final int count) {
    final List<Long> out = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final double factor = 0.5 + (i % 10) * 0.12; // ~0.5x .. ~1.6x
      out.add(Math.max(1L, Math.round(baseP50 * factor)));
    }
    return out;
  }

  private static long instanceCount(final String process, final int w) {
    return 20L + (w % 5) * 3L + PROCESSES.indexOf(process) * 4L;
  }

  private static long baseP50(final String process) {
    return switch (process) {
      case "order-process" -> 45_000L;
      case "payment-process" -> 120_000L;
      case "shipping-process" -> 600_000L;
      default -> 60_000L;
    };
  }

  private static long windowStart(final long tier, final int w) {
    final long alignedBase = BASE_MS - Math.floorMod(BASE_MS, tier);
    return alignedBase + (long) w * tier;
  }

  private void upsert(
      final CompiledDataset dataset,
      final CompiledTier tier,
      final DimensionKey key,
      final long windowStart,
      final List<Fact> facts) {
    writer.upsertCell(
        dataset, key, windowStart, tier.windowMs(), fold(dataset, facts), WriteVersion.SEED);
  }

  /** Folds the facts into the dataset's composite accumulator — every meter slot at once. */
  private static byte[] fold(final CompiledDataset dataset, final List<Fact> facts) {
    final List<BoundMeter<?, ?>> bounds = dataset.meterBounds();
    final CompositeAggregateFunction aggregate = new CompositeAggregateFunction(bounds);
    Object[] accumulator = aggregate.createAccumulator();
    for (final Fact fact : facts) {
      accumulator = aggregate.add(fact, accumulator);
    }
    return new CompositeAccumulatorValue(bounds).toBytes(accumulator);
  }
}
