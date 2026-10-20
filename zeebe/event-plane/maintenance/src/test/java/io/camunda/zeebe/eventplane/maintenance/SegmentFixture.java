/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.history.Histories;
import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.segment.EntryReader;
import io.camunda.zeebe.eventplane.segment.Footer;
import io.camunda.zeebe.eventplane.segment.Level;
import io.camunda.zeebe.eventplane.segment.SegmentWriter;
import io.camunda.zeebe.eventplane.snapshot.SnapshotArchive;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor;
import io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor.SnapshotFile;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Puts segments, histories and markers into a store the way a publisher and maintenance do. */
final class SegmentFixture {

  static final LogId LOG = new LogId("cluster-1", "default", 1);

  /** The latest record timestamp of a segment is this plus its last index, if it has records. */
  static final long TIMESTAMP = 1_792_152_000_000L;

  private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

  private final ObjectStore store;
  private final TestClock clock;
  private int sequence;

  SegmentFixture(final ObjectStore store, final TestClock clock) {
    this.store = store;
    this.clock = clock;
  }

  /** Puts a segment of these entries into a log, uploaded {@code age} ago; returns its key. */
  String put(final UUID history, final int level, final Duration age, final List<Entry> entries)
      throws IOException {
    return put(LOG, history, level, age, entries);
  }

  String put(
      final LogId log,
      final UUID history,
      final int level,
      final Duration age,
      final List<Entry> entries)
      throws IOException {
    final var out = new ByteArrayOutputStream();
    final var segmentLevel = level == 0 ? Level.PUBLISHER : Level.ROLLUP;
    try (final var writer = new SegmentWriter(out, log.identity(), history, segmentLevel)) {
      for (final var entry : entries) {
        writer.append(entry);
      }
      final long lastIndex = entries.getLast().index();
      writer.finish(entries.stream().anyMatch(Entry::hasRecords) ? TIMESTAMP + lastIndex : -1);
    }
    final var key =
        ObjectNames.segment(
            log.cluster(),
            log.tenant(),
            log.partition(),
            history,
            entries.getFirst().index(),
            entries.getLast().index(),
            level,
            ulid(clock.instant().minus(age)));
    store.put(key, out.toByteArray());
    return key;
  }

  /** Puts a retention marker with a ULID of {@code age} ago. */
  String putMarker(final Duration age) throws IOException {
    final var key =
        ObjectNames.retentionMarker(
            LOG.cluster(), LOG.tenant(), LOG.partition(), ulid(clock.instant().minus(age)));
    store.put(key, "{\"version\":1}".getBytes(StandardCharsets.UTF_8));
    return key;
  }

  /**
   * Puts a snapshot of partition 1 at an index, uploaded {@code age} ago, the way a publisher does:
   * a file, then the descriptor. It relies on the positions up to the entry after its index, {@code
   * 2 * (index + 1)}. Returns its id.
   */
  String putSnapshot(final UUID history, final Duration age, final long index) throws IOException {
    final var id = "%d-1-%d-%d-0-x".formatted(index, 2 * index + 1, 2 * index + 1);
    final var state = ("state at " + index).getBytes(StandardCharsets.UTF_8);
    store.put(
        ObjectNames.snapshotFile(LOG.cluster(), LOG.tenant(), LOG.partition(), id, "state"), state);
    final var descriptor =
        new SnapshotDescriptor(
            1,
            id,
            index,
            1,
            2 * index + 1,
            2 * index + 1,
            2 * index + 2,
            2 * index + 2,
            history,
            List.of(new SnapshotFile("state", state.length)),
            clock.instant().minus(age));
    store.put(
        ObjectNames.snapshotDescriptor(LOG.cluster(), LOG.tenant(), LOG.partition(), id),
        descriptor.toJson());
    return id;
  }

  /** The ids of partition 1's snapshots that have a descriptor, sorted. */
  List<String> snapshots() throws IOException {
    return SnapshotArchive.list(store, LOG.cluster(), LOG.tenant(), LOG.partition())
        .complete()
        .stream()
        .map(SnapshotDescriptor::snapshotId)
        .sorted()
        .toList();
  }

  /** Puts the part of a history's manifest that lists partition 1, created {@code age} ago. */
  void putHistory(final UUID history, final Duration age, final Base base) throws IOException {
    putHistory(history, age, Map.of(LOG.partition(), base));
  }

  void putHistory(final UUID history, final Duration age, final Map<Integer, Base> partitions)
      throws IOException {
    for (final var partition : partitions.entrySet()) {
      Histories.writePart(
          store,
          new HistoryManifest(
              1,
              LOG.cluster(),
              LOG.tenant(),
              history,
              clock.instant().minus(age),
              Map.of(partition.getKey(), partition.getValue())));
    }
  }

  /** The segments in a history's directory as {@code first-last Llevel}, in key order. */
  List<String> segments(final UUID history) throws IOException {
    return segments(LOG, history);
  }

  List<String> segments(final LogId log, final UUID history) throws IOException {
    final var names = new ArrayList<String>();
    for (final var key : keys(log, history)) {
      final var name = ObjectNames.parseSegment(key);
      names.add("%d-%d L%d".formatted(name.firstIndex(), name.lastIndex(), name.level()));
    }
    return names;
  }

  /** The keys of a history's segments, in key order. */
  List<String> keys(final UUID history) throws IOException {
    return keys(LOG, history);
  }

  private List<String> keys(final LogId log, final UUID history) throws IOException {
    return Listing.objects(store, log.segmentsPrefix(history)).stream()
        .map(StoredObject::key)
        .filter(key -> key.endsWith(Listing.SEGMENT_SUFFIX))
        .toList();
  }

  /** The keys of the log's retention markers, in key order. */
  List<String> markers() throws IOException {
    return Listing.objects(store, LOG.retentionPrefix()).stream().map(StoredObject::key).toList();
  }

  /** All the entries in a stored segment, as a reader reads them. */
  List<Entry> entries(final String key) throws Exception {
    final var content = store.get(key).orElseThrow();
    final var footer = Footer.read(ByteBuffer.wrap(content));
    final var entries = new ArrayList<Entry>();
    try (final var reader =
        new EntryReader(new ByteArrayInputStream(content, 0, (int) footer.bodyLength()))) {
      for (var entry = reader.next(); entry != null; entry = reader.next()) {
        entries.add(entry);
      }
    }
    return entries;
  }

  Footer footer(final String key) throws IOException {
    return Footer.read(ByteBuffer.wrap(store.get(key).orElseThrow()));
  }

  /** A ULID of a time, with the fixture's sequence number in its random part. */
  String ulid(final Instant time) {
    final var characters = new char[26];
    long millis = time.toEpochMilli();
    for (int i = 9; i >= 0; i--, millis >>>= 5) {
      characters[i] = ALPHABET.charAt((int) (millis & 31));
    }
    Arrays.fill(characters, 10, 26, '0');
    int next = ++sequence;
    for (int i = 25; i >= 23; i--, next >>>= 5) {
      characters[i] = ALPHABET.charAt(next & 31);
    }
    return new String(characters);
  }
}
