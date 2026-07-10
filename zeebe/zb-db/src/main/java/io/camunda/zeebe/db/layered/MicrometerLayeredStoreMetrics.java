/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import io.camunda.zeebe.db.layered.LayeredStateMetricsDoc.ElisionReason;
import io.camunda.zeebe.db.layered.LayeredStateMetricsDoc.Layer;
import io.camunda.zeebe.db.layered.LayeredStateMetricsDoc.LayeredStateKeyNames;
import io.camunda.zeebe.db.layered.LayeredStateMetricsDoc.ReadSource;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/**
 * Micrometer-backed {@link LayeredStoreMetrics} of one ownership domain. Every meter carries the
 * domain tag; all hot-path counters are pre-resolved at construction so counting never allocates.
 */
final class MicrometerLayeredStoreMetrics implements LayeredStoreMetrics {

  private final MeterRegistry registry;
  private final String domain;

  private final Counter annihilatedWrites;
  private final Counter drainSkippedTombstones;
  private final Counter cleanCacheHits;
  private final Counter delegateReadThroughs;
  private final Counter flushedPointReads;
  private final Counter pipelineMerges;
  private final Counter[] roundsByTrigger;
  private final Counter roundFailures;
  private final Timer roundDuration;
  private final Counter drainedEntries;
  private final Counter drainedBytes;
  private final Counter viewRotations;
  private final Counter viewAcquisitions;

  MicrometerLayeredStoreMetrics(final MeterRegistry registry, final String domain) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.domain = Objects.requireNonNull(domain, "domain");

    annihilatedWrites =
        elisionCounter(LayeredStateMetricsDoc.WRITES_ELIDED, ElisionReason.ANNIHILATED);
    drainSkippedTombstones =
        elisionCounter(LayeredStateMetricsDoc.WRITES_ELIDED, ElisionReason.DRAIN_SKIPPED);
    cleanCacheHits = readCounter(LayeredStateMetricsDoc.READS, ReadSource.CLEAN_CACHE);
    delegateReadThroughs = readCounter(LayeredStateMetricsDoc.READS, ReadSource.DELEGATE);
    flushedPointReads = counter(LayeredStateMetricsDoc.FLUSHED_POINT_READS);
    pipelineMerges = counter(LayeredStateMetricsDoc.PIPELINE_MERGES);
    final PersistTrigger[] triggers = PersistTrigger.values();
    roundsByTrigger = new Counter[triggers.length];
    for (final PersistTrigger trigger : triggers) {
      roundsByTrigger[trigger.ordinal()] =
          counterBuilder(LayeredStateMetricsDoc.PERSIST_ROUNDS)
              .tag(LayeredStateKeyNames.TRIGGER.asString(), trigger.getLabel())
              .register(registry);
    }
    roundFailures = counter(LayeredStateMetricsDoc.PERSIST_FAILURES);
    roundDuration =
        Timer.builder(LayeredStateMetricsDoc.PERSIST_DURATION.getName())
            .description(LayeredStateMetricsDoc.PERSIST_DURATION.getDescription())
            .serviceLevelObjectives(LayeredStateMetricsDoc.PERSIST_DURATION.getTimerSLOs())
            .tag(LayeredStateKeyNames.DOMAIN.asString(), domain)
            .register(registry);
    drainedEntries = counter(LayeredStateMetricsDoc.DRAINED_ENTRIES);
    drainedBytes = counter(LayeredStateMetricsDoc.DRAINED_BYTES);
    viewRotations = counter(LayeredStateMetricsDoc.VIEW_ROTATIONS);
    viewAcquisitions = counter(LayeredStateMetricsDoc.VIEW_ACQUISITIONS);
  }

  @Override
  public void countAnnihilatedWrites(final int elidedWrites) {
    annihilatedWrites.increment(elidedWrites);
  }

  @Override
  public void countDrainSkippedTombstone() {
    drainSkippedTombstones.increment();
  }

  @Override
  public void countCleanCacheHit() {
    cleanCacheHits.increment();
  }

  @Override
  public void countDelegateReadThrough() {
    delegateReadThroughs.increment();
  }

  @Override
  public void countFlushedPointRead() {
    flushedPointReads.increment();
  }

  @Override
  public void countPipelineMerge() {
    pipelineMerges.increment();
  }

  @Override
  public void countRound(final PersistTrigger trigger) {
    roundsByTrigger[trigger.ordinal()].increment();
  }

  @Override
  public void countRoundFailure() {
    roundFailures.increment();
  }

  @Override
  public void observeRoundDuration(final long elapsedNanos) {
    roundDuration.record(elapsedNanos, TimeUnit.NANOSECONDS);
  }

  @Override
  public void countDrainedEntry(final int keyBytes, final int valueBytes) {
    drainedEntries.increment();
    drainedBytes.increment(keyBytes + (double) valueBytes);
  }

  @Override
  public void countViewRotation() {
    viewRotations.increment();
  }

  @Override
  public void countViewAcquisition() {
    viewAcquisitions.increment();
  }

  @Override
  public void registerAnchorLag(final LongSupplier lag) {
    Gauge.builder(LayeredStateMetricsDoc.ANCHOR_LAG.getName(), lag::getAsLong)
        .description(LayeredStateMetricsDoc.ANCHOR_LAG.getDescription())
        .tag(LayeredStateKeyNames.DOMAIN.asString(), domain)
        .register(registry);
  }

  @Override
  public void registerStoreGauges(final Collection<LayeredKeyValueStore> stores) {
    layerGauge(
        LayeredStateMetricsDoc.BUFFERED_BYTES,
        Layer.STAGING,
        stores,
        LayeredKeyValueStore::stagingBytes);
    layerGauge(
        LayeredStateMetricsDoc.BUFFERED_BYTES,
        Layer.ACTIVE,
        stores,
        LayeredKeyValueStore::activeBytes);
    layerGauge(
        LayeredStateMetricsDoc.BUFFERED_BYTES,
        Layer.PIPELINE,
        stores,
        LayeredKeyValueStore::pipelineBytes);
    layerGauge(
        LayeredStateMetricsDoc.BUFFERED_BYTES,
        Layer.CLEAN,
        stores,
        LayeredKeyValueStore::cleanBytes);
    layerGauge(
        LayeredStateMetricsDoc.BUFFERED_ENTRIES,
        Layer.STAGING,
        stores,
        LayeredKeyValueStore::stagingEntryCount);
    layerGauge(
        LayeredStateMetricsDoc.BUFFERED_ENTRIES,
        Layer.ACTIVE,
        stores,
        LayeredKeyValueStore::activeEntryCount);
    layerGauge(
        LayeredStateMetricsDoc.BUFFERED_ENTRIES,
        Layer.PIPELINE,
        stores,
        LayeredKeyValueStore::pipelineEntryCount);
    layerGauge(
        LayeredStateMetricsDoc.BUFFERED_ENTRIES,
        Layer.CLEAN,
        stores,
        LayeredKeyValueStore::cleanEntryCount);
    Gauge.builder(
            LayeredStateMetricsDoc.PIPELINE_DEPTH.getName(),
            stores,
            MicrometerLayeredStoreMetrics::maxPipelineDepth)
        .description(LayeredStateMetricsDoc.PIPELINE_DEPTH.getDescription())
        .tag(LayeredStateKeyNames.DOMAIN.asString(), domain)
        .register(registry);
  }

  private void layerGauge(
      final ExtendedMeterDocumentation doc,
      final Layer layer,
      final Collection<LayeredKeyValueStore> stores,
      final ToLongFunction<LayeredKeyValueStore> value) {
    Gauge.builder(doc.getName(), stores, storesRef -> sum(storesRef, value))
        .description(doc.getDescription())
        .tag(LayeredStateKeyNames.DOMAIN.asString(), domain)
        .tag(LayeredStateKeyNames.LAYER.asString(), layer.getLabel())
        .register(registry);
  }

  private static double sum(
      final Collection<LayeredKeyValueStore> stores,
      final ToLongFunction<LayeredKeyValueStore> value) {
    long total = 0;
    for (final LayeredKeyValueStore store : stores) {
      total += value.applyAsLong(store);
    }
    return total;
  }

  private static double maxPipelineDepth(final Collection<LayeredKeyValueStore> stores) {
    int max = 0;
    for (final LayeredKeyValueStore store : stores) {
      max = Math.max(max, store.pipelineDepth());
    }
    return max;
  }

  private Counter counter(final ExtendedMeterDocumentation doc) {
    return counterBuilder(doc).register(registry);
  }

  private Counter elisionCounter(final ExtendedMeterDocumentation doc, final ElisionReason reason) {
    return counterBuilder(doc)
        .tag(LayeredStateKeyNames.ELISION_REASON.asString(), reason.getLabel())
        .register(registry);
  }

  private Counter readCounter(final ExtendedMeterDocumentation doc, final ReadSource source) {
    return counterBuilder(doc)
        .tag(LayeredStateKeyNames.READ_SOURCE.asString(), source.getLabel())
        .register(registry);
  }

  private Counter.Builder counterBuilder(final ExtendedMeterDocumentation doc) {
    return Counter.builder(doc.getName())
        .description(doc.getDescription())
        .tag(LayeredStateKeyNames.DOMAIN.asString(), domain);
  }
}
