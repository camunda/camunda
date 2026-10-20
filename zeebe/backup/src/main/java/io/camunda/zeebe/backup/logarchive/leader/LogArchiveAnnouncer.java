/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.leader.LogArchiveEventPlaneClient.Answer;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.scheduler.Actor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells the event plane which commit entries are in the bucket, in seq order, until it confirms
 * them (spec section 19.2). The resume point is the event plane's own {@code nextSeq}; nothing is
 * replicated. Only seqs the publisher has written are sent, taken from a bounded in-memory buffer
 * or, if they are not in it, read back from the bucket. Nothing waits for the announcer: when the
 * event plane is down, it only lags.
 *
 * <p>Only the current timeline is announced. A switch to another timeline drops the state of the
 * old one. A diverged timeline (spec section 8.4) is never announced.
 */
@NullMarked
public final class LogArchiveAnnouncer extends Actor {

  static final int BUFFER_SIZE = 1000;
  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveAnnouncer.class);
  private static final Duration INITIAL_BACKOFF = Duration.ofMillis(100);
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(10);

  private final int partitionId;
  private final LogArchiveStore store;
  private final LogArchiveEventPlaneClient client;
  private final int maxBatchEntries;

  // all fields below are only accessed on the actor thread
  private final TreeMap<Long, byte[]> buffer = new TreeMap<>();
  private boolean closed;
  // false until the publisher's recovery check succeeded
  private boolean recovered;
  private @Nullable String timelineId;
  private @Nullable String divergedTimelineId;
  private long highestPublishedSeq;
  // the event plane's expected seq as of its last answer, -1 if unknown
  private long nextSeq = -1;
  private boolean inFlight;
  private boolean retryPending;
  // incremented whenever state is discarded, so late callbacks are ignored
  private long generation;
  private Duration backoff = INITIAL_BACKOFF;

  public LogArchiveAnnouncer(
      final int partitionId,
      final LogArchiveStore store,
      final LogArchiveEventPlaneClient client,
      final int maxBatchEntries) {
    super(
        buildActorName("LogArchiveAnnouncer", partitionId),
        null,
        Map.of(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId)));
    this.partitionId = partitionId;
    this.store = store;
    this.client = client;
    this.maxBatchEntries = Math.max(1, maxBatchEntries);
  }

  /**
   * Called by the publisher once its recovery check succeeded, before any {@link #published}. The
   * announcer announces nothing earlier.
   *
   * @param bucketAhead the bucket is ahead of the recovered state: that timeline is not announced
   */
  public void onRecoveryChecked(final LogArchiveMeta meta, final boolean bucketAhead) {
    final String recoveredTimeline = meta.getTimelineId();
    final long lastPublishedSeq = meta.getLastPublishedSeq();
    actor.run(
        () -> {
          if (closed) {
            return;
          }
          reset();
          recovered = true;
          if (bucketAhead) {
            divergedTimelineId = recoveredTimeline;
          } else if (!recoveredTimeline.isEmpty()) {
            timelineId = recoveredTimeline;
            highestPublishedSeq = lastPublishedSeq;
          }
          drive();
        });
  }

  /** Called by the publisher after {@code commits/<seq>.json} (and the timeline file) is stored. */
  public void published(final String timeline, final long seq, final byte[] commitEntryJson) {
    actor.run(
        () -> {
          if (closed || !recovered || timeline.equals(divergedTimelineId)) {
            return;
          }
          if (!timeline.equals(timelineId)) {
            if (timelineId != null) {
              LOG.info(
                  "Partition {}: announcing timeline {} instead of {}",
                  partitionId,
                  timeline,
                  timelineId);
            }
            reset();
            timelineId = timeline;
          }
          buffer.put(seq, commitEntryJson);
          while (buffer.size() > BUFFER_SIZE) {
            buffer.pollFirstEntry();
          }
          highestPublishedSeq = Math.max(highestPublishedSeq, seq);
          drive();
        });
  }

  @Override
  protected void onActorClosing() {
    closed = true;
    generation++;
    buffer.clear();
  }

  private void reset() {
    generation++;
    buffer.clear();
    timelineId = null;
    divergedTimelineId = null;
    highestPublishedSeq = 0;
    nextSeq = -1;
    inFlight = false;
    retryPending = false;
    backoff = INITIAL_BACKOFF;
  }

  private void drive() {
    final var timeline = timelineId;
    if (closed || !recovered || inFlight || retryPending || timeline == null) {
      return;
    }
    if (highestPublishedSeq <= 0) {
      return;
    }
    final long first;
    if (nextSeq > 0) {
      first = nextSeq;
    } else {
      // the event plane's position is unknown: probe with the lowest seq at hand; the answer
      // tells where to continue
      first = buffer.isEmpty() ? highestPublishedSeq : buffer.firstKey();
    }
    if (first > highestPublishedSeq) {
      return;
    }
    final long last = Math.min(highestPublishedSeq, first + maxBatchEntries - 1);
    inFlight = true;
    final long expected = generation;
    load(timeline, first, last)
        .whenComplete(
            (entries, error) ->
                actor.run(
                    () -> {
                      if (expected != generation) {
                        return;
                      }
                      if (error != null) {
                        inFlight = false;
                        LOG.warn(
                            "Partition {}: failed to read commit entries {}..{} of timeline {},"
                                + " retrying in {}",
                            partitionId,
                            first,
                            last,
                            timeline,
                            backoff,
                            error);
                        retryLater();
                        return;
                      }
                      send(timeline, first, entries, expected);
                    }));
  }

  private CompletableFuture<List<byte[]>> load(
      final String timeline, final long first, final long last) {
    final var prefix = LogArchiveKeys.partitionPrefix(timeline, partitionId);
    final List<CompletableFuture<byte[]>> parts = new ArrayList<>();
    for (long seq = first; seq <= last; seq++) {
      final var buffered = buffer.get(seq);
      if (buffered != null) {
        parts.add(CompletableFuture.completedFuture(buffered));
        continue;
      }
      final long missing = seq;
      final var key = prefix + LogArchiveKeys.commitKey(seq);
      CompletableFuture<byte[]> read;
      try {
        read =
            store
                .getIfExists(key)
                .thenApply(
                    content ->
                        content.orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "Published commit entry " + missing + " not found: " + key)));
      } catch (final RuntimeException e) {
        read = CompletableFuture.failedFuture(e);
      }
      parts.add(read);
    }
    return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> parts.stream().map(CompletableFuture::join).toList());
  }

  private void send(
      final String timeline, final long first, final List<byte[]> entries, final long expected) {
    CompletableFuture<Answer> future;
    try {
      future = client.send(partitionId, timeline, entries);
    } catch (final RuntimeException e) {
      future = CompletableFuture.failedFuture(e);
    }
    future.whenComplete(
        (answer, error) ->
            actor.run(
                () -> {
                  if (expected != generation) {
                    return;
                  }
                  inFlight = false;
                  if (error != null) {
                    LOG.warn(
                        "Partition {}: failed to announce to the event plane, retrying in {}",
                        partitionId,
                        backoff,
                        error);
                    retryLater();
                  } else {
                    onAnswer(answer, first);
                  }
                }));
  }

  private void onAnswer(final Answer answer, final long first) {
    final int status = answer.status();
    if (status == 200 && answer.nextSeq() > 0 && answer.nextSeq() <= first) {
      // an applied batch moves the event plane past its first entry; without progress we would
      // resend the same batch in a hot loop
      LOG.warn(
          "Partition {}: the event plane applied seq {} but still expects {}, retrying in {}",
          partitionId,
          first,
          answer.nextSeq(),
          backoff);
      retryLater();
    } else if ((status == 200 || status == 409) && answer.nextSeq() > 0) {
      backoff = INITIAL_BACKOFF;
      nextSeq = answer.nextSeq();
      if (nextSeq > highestPublishedSeq + 1) {
        LOG.warn(
            "Partition {}: the event plane expects seq {} of timeline {}, but the highest published"
                + " seq is {}; waiting for the log archive to catch up",
            partitionId,
            nextSeq,
            timelineId,
            highestPublishedSeq);
      }
      drive();
    } else if (status == 400) {
      LOG.error(
          "Partition {}: the event plane rejected the commit entries of timeline {} as malformed"
              + " (this is a bug), retrying in {}",
          partitionId,
          timelineId,
          backoff);
      retryLater();
    } else {
      LOG.warn(
          "Partition {}: the event plane answered {}, retrying in {}",
          partitionId,
          status,
          backoff);
      retryLater();
    }
  }

  private void retryLater() {
    retryPending = true;
    final long expected = generation;
    final long delayMs = backoff.toMillis();
    backoff =
        backoff.multipliedBy(2).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff.multipliedBy(2);
    actor.schedule(
        Duration.ofMillis(delayMs),
        () -> {
          if (expected == generation) {
            retryPending = false;
            drive();
          }
        });
  }
}
