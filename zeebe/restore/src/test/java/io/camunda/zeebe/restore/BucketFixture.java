/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.eventplane.history.Histories;
import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.segment.Level;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.segment.SegmentWriter;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.Ulid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Puts what a publisher uploads into a bucket: history parts and segments. */
final class BucketFixture {

  static final String CLUSTER = "c";
  static final String TENANT = "default";
  static final int PARTITION = 1;
  static final LogId LOG = new LogId(CLUSTER, TENANT, PARTITION);

  private final ObjectStore store;

  BucketFixture(final ObjectStore store) {
    this.store = store;
  }

  /** Writes partition 1's part of a history's manifest. */
  void history(final UUID history, final Instant createdAt, final Base base) throws IOException {
    Histories.writePart(
        store,
        new HistoryManifest(1, CLUSTER, TENANT, history, createdAt, Map.of(PARTITION, base)));
  }

  /** Puts a segment of these entries into a history's directory; returns its key. */
  String segment(final UUID history, final int level, final List<Entry> entries)
      throws IOException {
    final var out = new ByteArrayOutputStream();
    try (final var writer =
        new SegmentWriter(out, LOG, history, level == 0 ? Level.PUBLISHER : Level.ROLLUP)) {
      for (final var entry : entries) {
        writer.append(entry);
      }
      writer.finish(entries.stream().anyMatch(Entry::hasRecords) ? 1_792_152_000_000L : -1);
    }
    final var key =
        ObjectNames.segment(
            LOG,
            history,
            entries.getFirst().index(),
            entries.getLast().index(),
            level,
            Ulid.next());
    store.put(key, out.toByteArray());
    return key;
  }
}
