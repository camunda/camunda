/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Counts what the store and its cursors do; nothing reads them in production yet. */
final class WriteBehindMetrics {

  final LongAdder iteratorsOpened = new LongAdder();
  final LongAdder keyIndexLoads = new LongAdder();
  final LongAdder keyIndexDiscardedLoads = new LongAdder();
  final LongAdder keyIndexDisables = new LongAdder();
  final LongAdder indexedScans = new LongAdder();
  final LongAdder keyIndexSwitches = new LongAdder();
  final LongAdder keyIndexHandovers = new LongAdder();
  final LongAdder keyIndexRefills = new LongAdder();
  final LongAdder keyIndexDiscardedRefills = new LongAdder();

  private final Map<Long, ColumnFamilyCounters> columnFamilies = new ConcurrentHashMap<>();

  ColumnFamilyCounters of(final long columnFamily) {
    return columnFamilies.computeIfAbsent(columnFamily, id -> new ColumnFamilyCounters());
  }

  /**
   * Registers one gauge per counter and column family, tagged with the column family id. Safe to
   * call repeatedly: a gauge that exists is kept.
   */
  void export(final MeterRegistry registry) {
    columnFamilies.forEach(
        (id, counters) ->
            counters
                .all()
                .forEach(
                    (name, adder) ->
                        Gauge.builder("zeebe.writebehind." + name, adder, LongAdder::doubleValue)
                            .tag("columnFamily", Long.toString(id))
                            .register(registry)));
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

    /** Point lookups answered "absent" by the key index. */
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
