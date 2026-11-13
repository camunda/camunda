/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.agrona.collections.Long2ObjectHashMap;

/**
 * Counts what the store, its flusher and its cursors do. The counters are plain adders on the hot
 * path; {@link #export} publishes them to a registry, which reads them when it is scraped.
 */
final class WriteBehindMetrics {

  private static final String PREFIX = "zeebe.writebehind.";

  final LongAdder iteratorsOpened = new LongAdder();

  /** Batches that the flusher wrote, and the time it took, including retries. */
  final LongAdder flushes = new LongAdder();

  final LongAdder flushNanos = new LongAdder();

  /** Writes that failed and were tried again, and the flushes that gave up. */
  final LongAdder flushRetries = new LongAdder();

  final LongAdder flushFailures = new LongAdder();

  /** Key indexes loaded from the storage, and loads thrown away because a commit overtook them. */
  final LongAdder keyIndexLoads = new LongAdder();

  final LongAdder keyIndexDiscardedLoads = new LongAdder();

  /** Key indexes given up for good, see {@link KeyIndex#disable}. */
  final LongAdder keyIndexDisables = new LongAdder();

  /** Scans that started on a key index instead of on the storage. */
  final LongAdder indexedScans = new LongAdder();

  /** Indexed scans that switched to the storage, because a commit changed the keys of the index. */
  final LongAdder keyIndexSwitches = new LongAdder();

  /** Indexed scans that went past the window of their index and continued on the storage. */
  final LongAdder keyIndexHandovers = new LongAdder();

  /** Windows that were refilled, and refills thrown away because a commit overtook them. */
  final LongAdder keyIndexRefills = new LongAdder();

  final LongAdder keyIndexDiscardedRefills = new LongAdder();

  // copied on write: a lookup by a primitive key, without a lock or a boxed key, on every read and
  // write; a column family is added once, when it is first used
  private volatile Long2ObjectHashMap<ColumnFamilyCounters> columnFamilies =
      new Long2ObjectHashMap<>();

  ColumnFamilyCounters of(final long columnFamily) {
    final var counters = columnFamilies.get(columnFamily);
    return counters != null ? counters : add(columnFamily);
  }

  private synchronized ColumnFamilyCounters add(final long columnFamily) {
    final var current = columnFamilies;
    final var existing = current.get(columnFamily);
    if (existing != null) {
      return existing;
    }
    final var counters = new ColumnFamilyCounters();
    final var copy = new Long2ObjectHashMap<ColumnFamilyCounters>();
    copy.putAll(current);
    copy.put(columnFamily, counters);
    columnFamilies = copy;
    return counters;
  }

  /**
   * Registers a counter per count and column family, and one per store-wide count, tagged with the
   * store name. Safe to call repeatedly: a meter that exists is kept, so call it again to pick up
   * column families that were first used since.
   */
  void export(final MeterRegistry registry, final String store) {
    columnFamilies.forEach(
        (id, counters) ->
            counters
                .all()
                .forEach(
                    (name, adder) ->
                        counter(
                            registry,
                            name,
                            adder,
                            Tags.of("store", store, "columnFamily", Long.toString(id)))));

    final var tags = Tags.of("store", store);
    counter(registry, "iterators.opened", iteratorsOpened, tags);
    counter(registry, "flush.retries", flushRetries, tags);
    counter(registry, "flush.failures", flushFailures, tags);
    counter(registry, "keyindex.loads", keyIndexLoads, tags);
    counter(registry, "keyindex.loads.discarded", keyIndexDiscardedLoads, tags);
    counter(registry, "keyindex.disables", keyIndexDisables, tags);
    counter(registry, "keyindex.switches", keyIndexSwitches, tags);
    counter(registry, "keyindex.handovers", keyIndexHandovers, tags);
    counter(registry, "keyindex.refills", keyIndexRefills, tags);
    counter(registry, "keyindex.refills.discarded", keyIndexDiscardedRefills, tags);
    counter(registry, "scan.indexed", indexedScans, tags);
    FunctionTimer.builder(
            PREFIX + "flush.duration",
            this,
            metrics -> metrics.flushes.sum(),
            metrics -> metrics.flushNanos.sum(),
            TimeUnit.NANOSECONDS)
        .tags(tags)
        .register(registry);
  }

  private static void counter(
      final MeterRegistry registry, final String name, final LongAdder adder, final Tags tags) {
    FunctionCounter.builder(PREFIX + name, adder, LongAdder::doubleValue)
        .tags(tags)
        .register(registry);
  }

  /** What the store did for the keys of one column family. */
  static final class ColumnFamilyCounters {
    /** Committed writes and deletes, before they are merged by the flusher. */
    final LongAdder committedPuts = new LongAdder();

    final LongAdder committedDeletes = new LongAdder();

    /** Point lookups answered by the unflushed versions. */
    final LongAdder dirtyHits = new LongAdder();

    /** Point lookups answered by the read cache. */
    final LongAdder cacheHits = new LongAdder();

    /** Point lookups answered "absent" by the key index of the column family. */
    final LongAdder keyIndexAbsent = new LongAdder();

    /** Point lookups that went to the storage, and how many of them found a value. */
    final LongAdder storageGets = new LongAdder();

    final LongAdder storageGetsFound = new LongAdder();

    /** Storage iterators opened for scans. */
    final LongAdder iterators = new LongAdder();

    /** Writes and deletes that the flusher put in a batch for the storage. */
    final LongAdder flushedPuts = new LongAdder();

    final LongAdder flushedDeletes = new LongAdder();

    /** Deletes that the flusher left out, because the storage never had the key. */
    final LongAdder skippedDeletes = new LongAdder();

    Map<String, LongAdder> all() {
      final var all = new LinkedHashMap<String, LongAdder>();
      all.put("committed.puts", committedPuts);
      all.put("committed.deletes", committedDeletes);
      all.put("lookup.dirty.hits", dirtyHits);
      all.put("lookup.cache.hits", cacheHits);
      all.put("lookup.keyindex.absent", keyIndexAbsent);
      all.put("lookup.storage.gets", storageGets);
      all.put("lookup.storage.found", storageGetsFound);
      all.put("scan.storage.iterators", iterators);
      all.put("flushed.puts", flushedPuts);
      all.put("flushed.deletes", flushedDeletes);
      all.put("flushed.deletes.skipped", skippedDeletes);
      return all;
    }
  }
}
