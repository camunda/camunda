/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.state.TestColumnFamilies;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The byte-equivalence theorem test (event-bridge-streaming ADR 0009 decision 6): a standby that
 * applies its changelog into a fresh store must end up byte-for-byte identical to the active's own
 * persisted store at the same cut — this is what lets a promoted standby's fold resume as though it
 * had simply restarted, and it is the property that replaces the engine's shared-fold-determinism
 * guarantee ADR 0006 originally relied on (source-fed standbys required two independent folds of
 * the same input to produce identical output; a changelog-applying standby instead replays the
 * active's own already-produced bytes, so there is nothing to independently compute and no
 * determinism contract to uphold).
 *
 * <p>This test drives a workload across several cuts — puts, an update, and deletes, spanning
 * multiple column families (approximating the analytics Stage-1 projection's multi-store shape) —
 * through the real {@link ChangelogPublisher} into a {@link FakeChangelogBroker} (a real on-wire
 * batch buffer, only the HTTP transport is faked), applies the identical rows directly to a
 * reference {@link RocksDbStateStoreProvider} standing in for "the active's own persisted store",
 * then runs the real {@link ChangelogApplier} against the same fake broker into a second, fresh
 * provider, and asserts every column family's contents match byte-for-byte, key and value.
 */
final class ChangelogApplierByteEquivalenceTest {

  private static final String TOPIC = "stage1-like-changelog";
  private static final int PARTITION = 0;

  @TempDir private Path activeDir;
  @TempDir private Path standbyDir;

  @Test
  void shouldReplicateMultiCutMultiColumnFamilyStateByteForByteIncludingDeletes() throws Exception {
    final var broker = new FakeChangelogBroker();
    final var publisher = new ChangelogPublisher(broker.client, TOPIC, PARTITION);

    try (var active =
        RocksDbStateStoreProvider.<TestColumnFamilies>open(
            activeDir.toFile(), new SimpleMeterRegistry())) {

      // cut 1: puts across two column families
      applyAndPublish(
          active,
          publisher,
          10L,
          row(TestColumnFamilies.CELLS, key(1), value("a")),
          row(TestColumnFamilies.OFFSETS, key(2), value("b")));

      // cut 2: an update, a delete, and a third column family
      applyAndPublish(
          active,
          publisher,
          20L,
          row(TestColumnFamilies.CELLS, key(1), value("a-updated")),
          tombstone(TestColumnFamilies.OFFSETS, key(2)),
          row(TestColumnFamilies.KV, key(3), value("c")));

      // cut 3: further churn, including deleting a row created two cuts ago
      applyAndPublish(
          active,
          publisher,
          30L,
          row(TestColumnFamilies.COMPOSITE, key(4), value("d")),
          tombstone(TestColumnFamilies.CELLS, key(1)));

      // when — a fresh standby applies the whole changelog
      final long[] persistedSourceOffset = {-1L};
      final long[] persistedPosition = {ChangelogApplier.NO_POSITION};
      try (var standby =
          RocksDbStateStoreProvider.<TestColumnFamilies>open(
              standbyDir.toFile(), new SimpleMeterRegistry())) {
        final var applier =
            ChangelogApplier.enveloped(
                broker.client,
                TOPIC,
                PARTITION,
                standby,
                ChangelogApplierByteEquivalenceTest::columnFamilyForTag,
                () -> persistedPosition[0],
                cut -> {
                  persistedSourceOffset[0] = cut.sourceOffset();
                  persistedPosition[0] = cut.changelogPosition();
                });
        applier.drainToEnd();

        // then — every column family matches the active's contents byte-for-byte
        for (final var cf : TestColumnFamilies.values()) {
          if (cf == TestColumnFamilies.DEFAULT) {
            continue;
          }
          assertThat(snapshot(standby, cf))
              .as("column family %s", cf)
              .isEqualTo(snapshot(active, cf));
        }
        // and — the marker's source offset (X) from the last cut was persisted alongside the rows
        assertThat(persistedSourceOffset[0]).isEqualTo(30L);
        assertThat(persistedPosition[0]).isEqualTo(applier.lastAppliedPosition());
      }
    }
  }

  private record Row(TestColumnFamilies cf, byte[] key, byte[] value, boolean tombstone) {}

  private static Row row(final TestColumnFamilies cf, final byte[] key, final byte[] value) {
    return new Row(cf, key, value, false);
  }

  private static Row tombstone(final TestColumnFamilies cf, final byte[] key) {
    return new Row(cf, key, new byte[0], true);
  }

  /**
   * Applies {@code rows} directly to {@code active} (simulating its own persist) and publishes the
   * same rows as one cut's changelog output (simulating {@code CommitCut#publish()}).
   */
  private static void applyAndPublish(
      final RocksDbStateStoreProvider<TestColumnFamilies> active,
      final ChangelogPublisher publisher,
      final long sourceOffset,
      final Row... rows) {
    active.runInTransaction(
        () -> {
          for (final var r : rows) {
            final var store = active.keyValueStore(r.cf(), new DbBytes(), new DbBytes());
            final var k = new DbBytes();
            k.wrapBytes(r.key());
            if (r.tombstone()) {
              store.delete(k);
            } else {
              final var v = new DbBytes();
              v.wrapBytes(r.value());
              store.put(k, v);
            }
          }
        });

    final List<ChangelogRecord> records = new ArrayList<>();
    for (final var r : rows) {
      final byte[] envelopedKey = ChangelogKeyEnvelope.encode(r.cf().getValue(), r.key());
      records.add(
          r.tombstone()
              ? ChangelogRecord.tombstone(envelopedKey)
              : ChangelogRecord.put(envelopedKey, r.value()));
    }
    publisher.publish(records, sourceOffset);
  }

  private static TestColumnFamilies columnFamilyForTag(final int tag) {
    for (final var cf : TestColumnFamilies.values()) {
      if (cf.getValue() == tag) {
        return cf;
      }
    }
    throw new IllegalArgumentException("no test column family for tag " + tag);
  }

  private static byte[] key(final long n) {
    final byte[] k = new byte[8];
    long remaining = n;
    for (int i = 7; i >= 0; i--) {
      k[i] = (byte) (remaining & 0xFF);
      remaining >>= 8;
    }
    return k;
  }

  private static byte[] value(final String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  /** A stable, comparable snapshot of a column family's contents: hex(key) -> hex(value). */
  private static Map<String, String> snapshot(
      final RocksDbStateStoreProvider<TestColumnFamilies> provider, final TestColumnFamilies cf) {
    final var store = provider.keyValueStore(cf, new DbBytes(), new DbBytes());
    final var snapshot = new TreeMap<String, String>();
    store.forEach(
        (k, v) ->
            snapshot.put(
                HexFormat.of().formatHex(k.getBytes()), HexFormat.of().formatHex(v.getBytes())));
    return snapshot;
  }
}
