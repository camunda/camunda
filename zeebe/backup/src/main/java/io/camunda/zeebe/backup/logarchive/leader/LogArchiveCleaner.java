/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.processing.LogArchiveListener;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes log objects a merge replaced, and orphans of cuts and merges that lost (spec section
 * 20.4). Runs only on the partition leader, every {@code cleanupInterval}.
 *
 * <p>Garbage is every data key of the current timeline with {@code firstIndex > R} and {@code
 * lastIndex <= M_safe} that is not the key of a merged object in the state. Keys above {@code
 * M_safe} are left alone, as an upload or a merge may be in flight there; keys at or below R belong
 * to the event plane; keys that do not parse as a data key (e.g. the event plane's rollups) are
 * skipped. A key is deleted once it has been garbage for {@code cleanupGracePeriod}, counted in
 * memory from when this leader first saw it, so that readers that resolved a replaced object before
 * its merge can finish.
 *
 * <p>Without the event plane, {@code M_safe} is M. With it, it is the merged index of the newest
 * {@code LOG_MERGED} entry the event plane has confirmed, so that it never lists an object the
 * cluster has deleted. A leader only knows the merged index of the merges it saw, and the one of
 * the state it recovered as of that state's last seq: until the event plane confirmed that seq, it
 * deletes nothing.
 *
 * <p>After every sweep, {@link #cleanedIndex()} is the highest index at or below which no garbage
 * object starts, pending or undeleted; the releaser never releases past it, so the event plane
 * never takes over an object the cluster would have deleted (spec section 20.4).
 *
 * <p>Like the merger, the cleaner does nothing before the publisher's recovery check, and nothing
 * on a timeline the bucket is ahead of (its later history would look like garbage) until a fork
 * started. A timeline started before merging existed has no merged index and nothing to clean.
 */
public final class LogArchiveCleaner extends Actor
    implements LogArchiveListener, LogArchiveRecoveryListener {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveCleaner.class);
  private static final Pattern DATA_KEY =
      Pattern.compile("^data/(\\d{20})-(\\d{20})-t\\d+\\.(?:seg|rec)$");

  private final int partitionId;
  private final LogArchiveStore store;
  private final LogArchiveStateView stateView;
  private final @Nullable ToLongFunction<String> confirmedNextSeq;
  private final Duration cleanupInterval;
  private final Duration gracePeriod;

  // all fields below are only accessed on the actor thread
  private boolean recovered;
  private boolean bucketAhead;
  // true once the timeline in the state is the one the archiver continues in the bucket
  private boolean ready;
  private String timelineId = "";
  // seq of a LOG_MERGED entry (or the last seq of the recovered state) -> M as of that seq
  private final TreeMap<Long, Long> mergedIndexBySeq = new TreeMap<>();
  // garbage key -> when this leader first saw it as garbage
  private final Map<String, Long> firstSeen = new HashMap<>();
  private boolean inFlight;
  private long epoch;
  private long sweepSafeMergedIndex;
  private volatile long cleanedIndex = -1;

  /**
   * @param stateView read access to the replicated state, used only by this cleaner
   * @param confirmedNextSeq the seq the event plane expects next per timeline, -1 if unknown (see
   *     {@link LogArchiveAnnouncer#confirmedNextSeq(String)}); null without the event plane
   */
  public LogArchiveCleaner(
      final int partitionId,
      final LogArchiveStore store,
      final LogArchiveStateView stateView,
      final @Nullable ToLongFunction<String> confirmedNextSeq,
      final Duration cleanupInterval,
      final Duration gracePeriod) {
    super(
        buildActorName("LogArchiveCleaner", partitionId),
        null,
        Map.of(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId)));
    if (cleanupInterval.isNegative() || cleanupInterval.isZero()) {
      throw new IllegalArgumentException(
          "cleanupInterval must be positive, but was " + cleanupInterval);
    }
    if (gracePeriod.isNegative()) {
      throw new IllegalArgumentException(
          "cleanupGracePeriod must not be negative, but was " + gracePeriod);
    }
    this.partitionId = partitionId;
    this.store = store;
    this.stateView = stateView;
    this.confirmedNextSeq = confirmedNextSeq;
    this.cleanupInterval = cleanupInterval;
    this.gracePeriod = gracePeriod;
  }

  @Override
  protected void onActorStarted() {
    actor.runAtFixedRate(cleanupInterval, this::clean);
  }

  @Override
  protected void onActorClosing() {
    epoch++;
    ready = false;
  }

  /**
   * The highest index C such that no garbage with {@code lastIndex <= C} was left by the last
   * completed sweep (not yet deleted, or not deletable). Never decreases within a timeline; before
   * the first completed sweep it is the released index. -1 before the recovery check. Safe to call
   * from any thread.
   */
  public long cleanedIndex() {
    return cleanedIndex;
  }

  // -----------------------------------------------------------------------------------------------
  // Inputs from other threads
  // -----------------------------------------------------------------------------------------------

  @Override
  public void onRecoveryChecked(final LogArchiveMeta meta, final boolean recoveredBehindBucket) {
    actor.run(
        () -> {
          resetTo(meta);
          recovered = true;
          bucketAhead = recoveredBehindBucket;
          ready = false;
        });
  }

  @Override
  public void onTimelineStarted(final String startedTimelineId) {
    actor.run(
        () -> {
          if (!recovered) {
            return;
          }
          try {
            resetTo(stateView.meta());
          } catch (final RuntimeException e) {
            LOG.warn("Partition {}: failed to read the state", partitionId, e);
            return;
          }
          bucketAhead = false;
          ready = true;
        });
  }

  @Override
  public void onLogArchived(final long archivedIndex, final long archivedPosition) {
    actor.run(
        () -> {
          if (recovered && !bucketAhead) {
            // the archiver resumed the recovered timeline: it continues it in the bucket
            ready = true;
          }
        });
  }

  @Override
  public void onLogMerged(final long seq, final long mergedIndex, final long mergedPosition) {
    actor.run(() -> mergedIndexBySeq.put(seq, mergedIndex));
  }

  // -----------------------------------------------------------------------------------------------
  // Cleaning (actor thread)
  // -----------------------------------------------------------------------------------------------

  /** Forgets everything of a previous timeline; the meta's M is known as of its last seq. */
  private void resetTo(final LogArchiveMeta meta) {
    epoch++;
    inFlight = false;
    timelineId = meta.getTimelineId();
    mergedIndexBySeq.clear();
    mergedIndexBySeq.put(meta.getNextSeq() - 1, meta.getMergedIndex());
    firstSeen.clear();
    cleanedIndex = meta.getReleasedIndex();
  }

  private void clean() {
    if (!ready || inFlight) {
      return;
    }
    final LogArchiveMeta meta;
    final Set<String> mergedKeys;
    try {
      meta = stateView.meta();
      if (meta.getTimelineId().isEmpty() || meta.getMergedIndex() < 0) {
        return;
      }
      if (!meta.getTimelineId().equals(timelineId)) {
        // a timeline started that this cleaner was not told about yet; start over with it
        resetTo(meta);
      }
      mergedKeys =
          stateView.mergedObjects().stream().map(LogObject::getKey).collect(Collectors.toSet());
    } catch (final RuntimeException e) {
      LOG.warn("Partition {}: failed to read the state, cleaning later", partitionId, e);
      return;
    }
    final long releasedIndex = meta.getReleasedIndex();
    final long safeMergedIndex = safeMergedIndex(meta);
    sweepSafeMergedIndex = safeMergedIndex;
    if (safeMergedIndex <= releasedIndex) {
      return;
    }

    final var prefix = LogArchiveKeys.partitionPrefix(timelineId, partitionId);
    final long expectedEpoch = epoch;
    inFlight = true;
    CompletableFuture<List<String>> listed;
    try {
      listed = store.list(prefix + "data/");
    } catch (final RuntimeException e) {
      listed = CompletableFuture.failedFuture(e);
    }
    listed.whenComplete(
        (keys, error) ->
            actor.run(
                () -> {
                  if (expectedEpoch != epoch) {
                    return;
                  }
                  if (error != null) {
                    inFlight = false;
                    LOG.warn("Partition {}: failed to list {}", partitionId, prefix, error);
                    return;
                  }
                  final var garbage =
                      garbage(prefix, keys, releasedIndex, safeMergedIndex, mergedKeys);
                  deleteDue(garbage, expectedEpoch);
                }));
  }

  /**
   * M_safe: M without the event plane; with it, M as of the newest seq the event plane confirmed,
   * or -1 if no such seq is known.
   */
  private long safeMergedIndex(final LogArchiveMeta meta) {
    if (confirmedNextSeq == null) {
      return meta.getMergedIndex();
    }
    final long next = confirmedNextSeq.applyAsLong(timelineId);
    final var confirmed = next > 0 ? mergedIndexBySeq.floorEntry(next - 1) : null;
    if (confirmed == null) {
      return -1;
    }
    // older merges are covered by the confirmed one
    mergedIndexBySeq.headMap(confirmed.getKey(), false).clear();
    return Math.min(confirmed.getValue(), meta.getMergedIndex());
  }

  private static Set<String> garbage(
      final String prefix,
      final List<String> keys,
      final long releasedIndex,
      final long safeMergedIndex,
      final Set<String> mergedKeys) {
    final Set<String> garbage = new HashSet<>();
    for (final var key : keys) {
      if (!key.startsWith(prefix)) {
        continue;
      }
      final var relativeKey = key.substring(prefix.length());
      final var matcher = DATA_KEY.matcher(relativeKey);
      if (!matcher.matches()) {
        continue;
      }
      final long firstIndex = Long.parseLong(matcher.group(1));
      final long lastIndex = Long.parseLong(matcher.group(2));
      if (firstIndex > releasedIndex
          && lastIndex <= safeMergedIndex
          && !mergedKeys.contains(relativeKey)) {
        garbage.add(key);
      }
    }
    return garbage;
  }

  private void deleteDue(final Set<String> garbage, final long expectedEpoch) {
    final long now = ActorClock.currentTimeMillis();
    firstSeen.keySet().retainAll(garbage);
    garbage.forEach(key -> firstSeen.putIfAbsent(key, now));
    final List<String> due = new ArrayList<>();
    firstSeen.forEach(
        (key, seen) -> {
          if (now - seen >= gracePeriod.toMillis()) {
            due.add(key);
          }
        });
    if (due.isEmpty()) {
      inFlight = false;
      advanceCleanedIndex();
      return;
    }
    due.sort(null);
    LOG.debug(
        "Partition {}: deleting {} replaced or orphaned log objects", partitionId, due.size());
    deleteNext(due, 0, expectedEpoch);
  }

  /** Called at the end of a sweep: {@code firstSeen} holds the garbage that is still there. */
  private void advanceCleanedIndex() {
    long cleaned = sweepSafeMergedIndex;
    for (final var key : firstSeen.keySet()) {
      final var matcher = DATA_KEY.matcher(key.substring(key.indexOf("data/")));
      if (matcher.matches()) {
        // below its first index, not its last: the cluster may delete only objects above R, so an
        // object that R reached into would stay behind for good
        cleaned = Math.min(cleaned, Long.parseLong(matcher.group(1)) - 1);
      }
    }
    cleanedIndex = Math.max(cleanedIndex, cleaned);
  }

  /** Deletes one key after the other, so a large backlog does not flood the store. */
  private void deleteNext(final List<String> due, final int next, final long expectedEpoch) {
    if (next == due.size()) {
      inFlight = false;
      advanceCleanedIndex();
      LOG.info(
          "Partition {}: deleted {} replaced or orphaned log objects", partitionId, due.size());
      return;
    }
    final var key = due.get(next);
    CompletableFuture<Void> deleted;
    try {
      deleted = store.delete(key);
    } catch (final RuntimeException e) {
      deleted = CompletableFuture.failedFuture(e);
    }
    deleted.whenComplete(
        (ignored, error) ->
            actor.run(
                () -> {
                  if (expectedEpoch != epoch) {
                    return;
                  }
                  if (error != null) {
                    inFlight = false;
                    advanceCleanedIndex();
                    LOG.warn(
                        "Partition {}: failed to delete {}, trying again on the next run",
                        partitionId,
                        key,
                        error);
                    return;
                  }
                  firstSeen.remove(key);
                  deleteNext(due, next + 1, expectedEpoch);
                }));
  }
}
