/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.history.Histories;
import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.segment.Identity;
import io.camunda.zeebe.eventplane.segment.Level;
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

/** Writes a log's objects into a store, as publishers and maintenance do. */
record TestLog(ObjectStore store, LogId log) {

  /** Writes the log's part of a history's manifest. */
  void history(final UUID history, final Base base, final Instant createdAt) throws IOException {
    Histories.writePart(
        store,
        new HistoryManifest(
            1, log.cluster(), log.tenant(), history, createdAt, Map.of(log.partition(), base)));
  }

  /** Uploads entries as a publisher's segment, and returns its key. */
  String upload(final UUID history, final List<Entry> entries) throws IOException {
    return upload(history, Level.PUBLISHER, entries);
  }

  /** Uploads entries as a segment of a level, and returns its key. */
  String upload(final UUID history, final Level level, final List<Entry> entries)
      throws IOException {
    final var out = new ByteArrayOutputStream();
    try (var writer =
        new SegmentWriter(
            out, new Identity(log.cluster(), log.tenant(), log.partition()), history, level)) {
      entries.forEach(
          entry -> {
            try {
              writer.append(entry);
            } catch (final IOException e) {
              throw new IllegalStateException(e);
            }
          });
      final boolean records = entries.stream().anyMatch(Entry::hasRecords);
      writer.finish(records ? 1_000L : Entry.NO_POSITION);
    }
    final var key =
        ObjectNames.segment(
            log.cluster(),
            log.tenant(),
            log.partition(),
            history,
            entries.getFirst().index(),
            entries.getLast().index(),
            level == Level.PUBLISHER ? 0 : 1,
            Ulid.next());
    store.put(key, out.toByteArray());
    return key;
  }

  /** Writes a retention marker, which keeps entries from an index on. */
  void retention(final UUID history, final long firstIndex, final long firstPosition)
      throws IOException {
    store.put(
        ObjectNames.retentionMarker(log.cluster(), log.tenant(), log.partition(), Ulid.next()),
        new RetentionMarker(history, firstIndex, firstPosition, Instant.now()).toJson());
  }

  void delete(final String key) throws IOException {
    store.delete(key);
  }
}
