/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.FetchResult;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.api.StateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

/**
 * A standby shard's changelog follower (event-bridge-streaming ADR 0009 decision 6, consumer-groups
 * ADR 0006 decision 1): tails a partition's changelog and applies its cuts to a local store,
 * byte-for-byte, without ever fetching or folding the source. Runs instead of a {@link
 * io.camunda.eventbridge.streaming.Task} while a shard holds the STANDBY role.
 *
 * <p><b>Cut-atomic apply.</b> Records are buffered in memory until their cut's offset-marker
 * arrives ({@link ChangelogMarker} is strictly the last record of every cut) — a buffered row is
 * never visible in the store before its marker lands. Once the marker arrives, the whole buffered
 * cut is applied as one {@link StateStoreProvider#runInTransaction transaction}: every row put or
 * deleted, plus the caller's own persistence of the cut's source offset and changelog position
 * ({@link #onCutApplied}), so those bookkeeping fields advance in the exact same transaction the
 * active would use — a promoted fold sees a {@link io.camunda.eventbridge.streaming.Task#restore()}
 * baseline it cannot distinguish from a normal restart. A writer crashing mid-publish leaves a torn
 * tail — records after the last marker in a fetch — which simply stays buffered, invisible, until
 * either its marker eventually arrives or a new active's first cut supersedes it; it is never
 * applied on its own.
 *
 * <p><b>Ingestion path.</b> Each row's key is either the store's own key directly (a single-column-
 * family shard) or a {@link ChangelogKeyEnvelope}-wrapped key naming which column family it belongs
 * to (a multi-column-family shard, e.g. the analytics Stage-1 projection). Either way, the row is
 * put/deleted through the same {@link KeyValueStore} interface the active's own store uses — raw,
 * already-serialized bytes in, raw bytes out ({@link DbBytes}, an identity codec) — never a
 * store-specific write path. This matters for recovery-grade correctness: a store's own restore
 * logic (e.g. {@code SegmentSealingAggregation.recover()}) rebuilds its in-heap bookkeeping (like a
 * durably-written set) purely by scanning what is physically present in its column family at
 * construction time. Since this applier writes into that exact column family using that exact
 * key/value byte layout, the normal task-construction restore path reconstructs the standby's
 * bookkeeping correctly with no special-casing — it cannot tell the rows arrived via changelog
 * apply rather than the active's own fold-and-persist.
 *
 * <p><b>Resume authority.</b> A restarting applier with intact local state resumes from its own
 * last applied changelog position {@code P} (supplied via {@link #lastPersistedPosition}), never
 * from the source offset or the consumer group's committed offset. This is deliberate: the cut
 * chain orders the changelog ack strictly before the local commit and the local commit strictly
 * before the chained {@code commitOffset(X)} (ADR 0009 decisions 1/2), so the changelog position
 * for any given cut is always durable at least as early as that cut's group-committed offset — the
 * marker's {@code X} is never behind the group offset, only ever equal or ahead of what the group
 * has observed. Resuming from the marker is therefore always at least as fresh as resuming from the
 * group offset would be, and is the only position this applier ever persists in the first place.
 *
 * <p><b>Warming.</b> An empty local store has no persisted position: {@link #lastPersistedPosition}
 * returns {@link #NO_POSITION} and the applier starts tailing from the changelog's first record
 * (position {@code 0}) — a full cold rebuild, bounded by the live keyspace rather than history. An
 * intact disk resumes from {@code P + 1}. Both paths run the identical {@link #pollOnce()} loop;
 * warming is not a special case.
 *
 * <p><b>Readiness</b> is the changelog's high watermark (as observed by the most recent fetch)
 * minus the last applied position — the lag a standby reports on its heartbeat (consumer-groups ADR
 * 0006 decision 1). Unknown until the first fetch completes, at which point it reflects real lag.
 *
 * @param <CF> the caller's column-family enum, exactly as passed to its own {@link
 *     StateStoreProvider}
 */
public final class ChangelogApplier<
        CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements AutoCloseable {

  /** Sentinel meaning "no local state; start tailing the changelog from position 0". */
  public static final long NO_POSITION = -1L;

  /** How many bytes to request per fetch — generous enough to catch up several cuts at once. */
  private static final int FETCH_MAX_BYTES = 4 * 1024 * 1024;

  private final EventBridgeClient client;
  private final String topic;
  private final int partitionIndex;
  private final StateStoreProvider<CF> provider;
  private final IntFunction<CF> columnFamilyForTag;
  private final boolean enveloped;
  private final CF fixedColumnFamily;
  private final Consumer<CutPositions> onCutApplied;

  private final Map<CF, KeyValueStore<DbBytes, DbBytes>> stores = new HashMap<>();
  private final List<BufferedRow<CF>> buffer = new ArrayList<>();

  private long nextFetchPosition;
  private long lastAppliedPosition;
  private long observedHighWatermark = -1L;

  private ChangelogApplier(
      final EventBridgeClient client,
      final String topic,
      final int partitionIndex,
      final StateStoreProvider<CF> provider,
      final boolean enveloped,
      final CF fixedColumnFamily,
      final IntFunction<CF> columnFamilyForTag,
      final LongSupplier lastPersistedPosition,
      final Consumer<CutPositions> onCutApplied) {
    this.client = client;
    this.topic = topic;
    this.partitionIndex = partitionIndex;
    this.provider = provider;
    this.enveloped = enveloped;
    this.fixedColumnFamily = fixedColumnFamily;
    this.columnFamilyForTag = columnFamilyForTag;
    this.onCutApplied = onCutApplied;
    lastAppliedPosition = lastPersistedPosition.getAsLong();
    nextFetchPosition = lastAppliedPosition == NO_POSITION ? 0 : lastAppliedPosition + 1;
  }

  /**
   * An applier for a shard whose changelog carries rows from a single column family, unenveloped.
   */
  public static <CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ChangelogApplier<CF> singleColumnFamily(
          final EventBridgeClient client,
          final String topic,
          final int partitionIndex,
          final StateStoreProvider<CF> provider,
          final CF columnFamily,
          final LongSupplier lastPersistedPosition,
          final Consumer<CutPositions> onCutApplied) {
    return new ChangelogApplier<>(
        client,
        topic,
        partitionIndex,
        provider,
        false,
        columnFamily,
        tag -> columnFamily,
        lastPersistedPosition,
        onCutApplied);
  }

  /**
   * An applier for a shard whose changelog spans several column families through a {@link
   * ChangelogKeyEnvelope} (e.g. the analytics Stage-1 projection changelog).
   */
  public static <CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ChangelogApplier<CF> enveloped(
          final EventBridgeClient client,
          final String topic,
          final int partitionIndex,
          final StateStoreProvider<CF> provider,
          final IntFunction<CF> columnFamilyForTag,
          final LongSupplier lastPersistedPosition,
          final Consumer<CutPositions> onCutApplied) {
    return new ChangelogApplier<>(
        client,
        topic,
        partitionIndex,
        provider,
        true,
        null,
        columnFamilyForTag,
        lastPersistedPosition,
        onCutApplied);
  }

  /**
   * Fetches the next batch and applies every complete cut it contains (zero, one, or several — a
   * standby catching up from far behind may cross many cuts in one poll). Any records after the
   * last marker in the batch stay buffered, unapplied, for the next call. Returns the number of
   * cuts applied (possibly zero).
   */
  public int pollOnce() {
    final FetchResult result =
        client.fetchFromTopic(topic, partitionIndex, nextFetchPosition, FETCH_MAX_BYTES).join();

    if (result.isOutOfRange()) {
      // The requested position fell below the earliest retained (compacted) record. A standby
      // resuming from a stale local position this far behind has no correct choice but a full
      // rebuild from the start; this is the same recourse a cold joiner takes.
      nextFetchPosition = 0;
      buffer.clear();
      return 0;
    }
    if (!result.isSuccess() || result.isEmpty()) {
      if (result.isSuccess()) {
        observedHighWatermark = Math.max(observedHighWatermark, result.highWatermark());
      }
      return 0;
    }

    observedHighWatermark = Math.max(observedHighWatermark, result.highWatermark());

    int applied = 0;
    long lastSeenPosition = nextFetchPosition - 1;
    for (final var entry : result.entriesWithKeys(nextFetchPosition)) {
      lastSeenPosition = entry.position();
      if (ChangelogMarker.isMarkerKey(entry.key())) {
        final long sourceOffset = ChangelogMarker.decodeSourceOffset(entry.value());
        applyBufferedCut(sourceOffset, entry.position());
        applied++;
      } else {
        buffer.add(bufferRow(entry.key(), entry.value()));
      }
    }
    nextFetchPosition = lastSeenPosition + 1;
    return applied;
  }

  /**
   * Polls until the applier has caught up to the changelog's high watermark observed at the moment
   * this is called — used for promotion (drain to the last marker) and for a cold joiner's initial
   * warming pass. Returns once no cut remains to apply that was already durable when this started;
   * it does not chase a moving target indefinitely.
   */
  public void drainToEnd() {
    int applied = pollOnce();
    // Fixed once the first poll observes a real watermark: draining targets what was durable when
    // this was called, not whatever a still-writing active produces afterward.
    final long target = observedHighWatermark;
    while (target >= 0 && lastAppliedPosition < target - 1) {
      applied = pollOnce();
      if (applied == 0) {
        break; // nothing left to apply toward the target — at most a torn tail remains
      }
    }
  }

  /** The last changelog position this applier has fully applied (a cut's marker position). */
  public long lastAppliedPosition() {
    return lastAppliedPosition;
  }

  /**
   * Changelog lag: the high watermark last observed minus the last applied position. Reported on
   * the standby's heartbeat (consumer-groups ADR 0006 decision 1); {@code 0} means caught up to
   * what this applier has seen so far. Conservatively large before the first successful fetch.
   */
  public long readiness() {
    if (observedHighWatermark < 0) {
      return Long.MAX_VALUE;
    }
    final long applied = lastAppliedPosition == NO_POSITION ? -1L : lastAppliedPosition;
    return Math.max(0L, observedHighWatermark - 1 - applied);
  }

  @Override
  public void close() {
    // The provider (the standby's store) is intentionally not closed here: on promotion it is
    // handed to the fold path unchanged (event-bridge-streaming ADR 0009 decision 6 / consumer-
    // groups ADR 0006 decision 1's promotion lifecycle) — closing it is the caller's decision, made
    // only once no role (standby or active) needs the store anymore.
  }

  private void applyBufferedCut(final long sourceOffset, final long changelogPosition) {
    final var rows = List.copyOf(buffer);
    buffer.clear();
    provider.runInTransaction(
        () -> {
          for (final var row : rows) {
            final var store = storeFor(row.columnFamily());
            final var key = new DbBytes();
            key.wrapBytes(row.storeKey());
            if (row.tombstone()) {
              store.delete(key);
            } else {
              final var value = new DbBytes();
              value.wrapBytes(row.value());
              store.put(key, value);
            }
          }
          onCutApplied.accept(new CutPositions(sourceOffset, changelogPosition));
        });
    lastAppliedPosition = changelogPosition;
  }

  private BufferedRow<CF> bufferRow(final byte[] key, final byte[] value) {
    if (enveloped) {
      final var envelope = ChangelogKeyEnvelope.decode(key);
      final var cf = columnFamilyForTag.apply(envelope.cfTag());
      return new BufferedRow<>(cf, envelope.storeKey(), value, value.length == 0);
    }
    return new BufferedRow<>(fixedColumnFamily, key, value, value.length == 0);
  }

  private KeyValueStore<DbBytes, DbBytes> storeFor(final CF columnFamily) {
    return stores.computeIfAbsent(
        columnFamily, cf -> provider.keyValueStore(cf, new DbBytes(), new DbBytes()));
  }

  /** A row buffered for the in-flight (not yet marker-terminated) cut. */
  private record BufferedRow<CF>(
      CF columnFamily, byte[] storeKey, byte[] value, boolean tombstone) {}

  /**
   * The source offset and changelog position one applied cut carries — a standby persists both in
   * the same transaction as the row puts/deletes, the exact shape the active persists (ADR 0009
   * decision 6). The caller writes these through its own column family/key encoding, mirroring
   * whatever its active {@link io.camunda.eventbridge.streaming.CommitCut#persist()} does.
   */
  public record CutPositions(long sourceOffset, long changelogPosition) {}
}
