/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.actor;

import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.eventbridge.broker.logstream.LogRecordAwaiter;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.core.logappend.EventLogAppendEntry;
import io.camunda.eventbridge.core.logappend.RawEventRecordValue;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor responsible for sequencing incoming publish requests onto the partition's {@link
 * io.camunda.zeebe.logstreams.log.LogStream}.
 *
 * <p>Also serves long-poll requests by maintaining a list of {@link LogRecordAwaiter}s and
 * notifying them when new entries are written to the log.
 *
 * <p>The actor starts disconnected (writer/reader may be {@code null}). Once the RAFT partition for
 * this actor's partition elects a leader, {@link #connect} is called to wire up the live {@link
 * LogStreamWriter} and {@link LogStreamReader}. On role loss, {@link #disconnect} is called.
 */
public final class PublishActor extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(PublishActor.class);

  private final int partitionId;
  private LogStreamWriter logStreamWriter;
  private LogStreamReader logStreamReader;
  private final EventBridgeProperties properties;

  /**
   * Non-null only while this node is the RAFT leader for this partition. Set by {@link
   * #connect(LogStreamWriter, LogStreamReader, RaftPartitionServer)}, cleared by {@link
   * #disconnect()}.
   */
  private RaftPartitionServer raftPartitionServer;

  /** Parked long-poll awaiters waiting for new records on this partition. */
  private final List<LogRecordAwaiter> pendingAwaiters = new CopyOnWriteArrayList<>();

  public PublishActor(
      final int partitionId,
      final LogStreamWriter logStreamWriter,
      final LogStreamReader logStreamReader,
      final EventBridgeProperties properties) {
    this.partitionId = partitionId;
    this.logStreamWriter = logStreamWriter;
    this.logStreamReader = logStreamReader;
    this.properties = properties;
  }

  @Override
  public String getName() {
    return "event-bridge-publish-" + partitionId;
  }

  /**
   * Convenience overload for tests and scenarios where no {@link RaftPartitionServer} is needed
   * (e.g. unit tests that do not exercise RAFT log compaction).
   *
   * @see #connect(LogStreamWriter, LogStreamReader, RaftPartitionServer)
   */
  public ActorFuture<Void> connect(final LogStreamWriter writer, final LogStreamReader reader) {
    return connect(writer, reader, null);
  }

  /**
   * Connects this actor to a live {@link LogStreamWriter}, {@link LogStreamReader}, and the
   * underlying {@link RaftPartitionServer}.
   *
   * <p>Called by {@link io.camunda.eventbridge.broker.partition.EventBridgePartition} when this
   * node becomes the RAFT leader for this partition. The update is posted to the actor's own thread
   * to avoid data races.
   *
   * @param writer the log-stream writer to use for publish requests
   * @param reader the log-stream reader to use for position queries
   * @param raftServer the RAFT partition server, used for log compaction during truncation
   * @return a future that completes when the actor thread has applied the connection
   */
  public ActorFuture<Void> connect(
      final LogStreamWriter writer,
      final LogStreamReader reader,
      final RaftPartitionServer raftServer) {
    final var future = new CompletableActorFuture<Void>();
    actor.call(
        () -> {
          logStreamWriter = writer;
          logStreamReader = reader;
          raftPartitionServer = raftServer;
          LOG.info("Partition {} PublishActor connected to LogStream", partitionId);
          future.complete(null);
        });
    return future;
  }

  /**
   * Disconnects this actor from the current {@link LogStreamWriter} and {@link LogStreamReader}.
   *
   * <p>Called by {@link io.camunda.eventbridge.broker.partition.EventBridgePartition} when this
   * node loses RAFT leadership. After disconnect, publish and long-poll requests fail fast with a
   * descriptive error until {@link #connect} is called again.
   *
   * @return a future that completes when the actor thread has applied the disconnection
   */
  public ActorFuture<Void> disconnect() {
    final var future = new CompletableActorFuture<Void>();
    actor.call(
        () -> {
          logStreamWriter = null;
          logStreamReader = null;
          raftPartitionServer = null;
          LOG.info("Partition {} PublishActor disconnected from LogStream", partitionId);
          future.complete(null);
        });
    return future;
  }

  /**
   * Publishes a batch of raw event payloads to the log. Each event receives its own log position.
   *
   * @param payloads raw event byte arrays, one per event
   * @return a future that resolves to the list of log positions assigned to each event in order
   */
  public ActorFuture<List<Long>> publishBatch(final List<byte[]> payloads) {
    final var result = new CompletableActorFuture<List<Long>>();
    actor.call(
        () -> {
          if (logStreamWriter == null) {
            result.completeExceptionally(
                new IllegalStateException(
                    "Partition " + partitionId + " is not yet leader; LogStream not connected."));
            return;
          }
          final var entries =
              new ArrayList<io.camunda.zeebe.logstreams.log.LogAppendEntry>(payloads.size());
          for (final byte[] payload : payloads) {
            final var entry = new EventLogAppendEntry();
            entry.wrap(-1L, payload);
            entries.add(entry);
          }
          final var writeResult = logStreamWriter.tryWrite(WriteContext.internal(), entries, -1);
          if (writeResult.isRight()) {
            // The LogStreamWriter returns the highest position in the batch.
            // Positions are sequential: highestPos - n + 1 … highestPos
            final long highest = writeResult.get();
            final int count = payloads.size();
            final List<Long> positions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
              positions.add(highest - (count - 1 - i));
            }
            notifyAwaiters();
            result.complete(positions);
          } else {
            result.completeExceptionally(
                new RuntimeException(
                    "Log write failed on partition " + partitionId + ": " + writeResult.getLeft()));
          }
        });
    return result;
  }

  /**
   * Parks a long-poll caller until a new record is available at or after {@code fromPosition}.
   *
   * <p>The returned future is completed (with {@code null}) when either a new record is available
   * or {@code waitMs} elapses. No CPU thread is blocked.
   *
   * @param fromPosition the position to check for available records before parking
   * @param waitMs maximum wait in milliseconds; clamped to the configured long-poll ceiling
   * @return a future that completes when records are available or the wait window elapses
   */
  public ActorFuture<Void> awaitRecords(final long fromPosition, final int waitMs) {
    final var result = new CompletableActorFuture<Void>();
    actor.call(
        () -> {
          if (logStreamReader == null) {
            result.completeExceptionally(
                new IllegalStateException(
                    "Partition " + partitionId + " is not yet leader; LogStream not connected."));
            return;
          }
          final int clamped = Math.min(waitMs, properties.broker().longPoll().maxWaitMs());

          // Declare awaiter before scheduling the timer so the timer callback can reference it.
          final ScheduledTimer[] timerRef = new ScheduledTimer[1];
          final var awaiter =
              new LogRecordAwaiter() {
                @Override
                public CompletableFuture<Void> awaitRecord(final long timeoutMs) {
                  return result.toCompletableFuture();
                }

                @Override
                public void onRecordAvailable() {
                  // Cancel the timeout timer and complete the future from the actor thread
                  actor.submit(
                      () -> {
                        if (timerRef[0] != null) {
                          timerRef[0].cancel();
                        }
                        pendingAwaiters.remove(this);
                        result.complete(null);
                      });
                }
              };

          // Schedule a timer to complete the future after the wait window.
          // Remove the awaiter (not result) to prevent memory leak if no record arrives.
          timerRef[0] =
              actor.schedule(
                  Duration.ofMillis(clamped),
                  () -> {
                    pendingAwaiters.remove(awaiter);
                    result.complete(null);
                  });

          pendingAwaiters.add(awaiter);
        });
    return result;
  }

  /**
   * Returns the current tail position of the log (the position at which the next written event
   * would be placed).
   */
  public ActorFuture<Long> getLatestPosition() {
    final var result = new CompletableActorFuture<Long>();
    actor.call(
        () -> {
          if (logStreamReader == null) {
            result.completeExceptionally(
                new IllegalStateException(
                    "Partition " + partitionId + " is not yet leader; LogStream not connected."));
            return;
          }
          final long pos = logStreamReader.seekToEnd();
          result.complete(Math.max(0L, pos));
        });
    return result;
  }

  /**
   * Reads up to {@code maxRecords} entries from the log starting at {@code fromPosition}.
   *
   * <p>Position resolution rules:
   *
   * <ul>
   *   <li>{@code fromPosition < 0} — seek to the first available entry (oldest retained).
   *   <li>{@code fromPosition >= 0} — seek to that exact position. If the position was truncated
   *       (i.e., no entry exists at or before it but entries exist after it), returns {@link
   *       PollRecordsResult.PositionTruncated} with the oldest available position.
   *   <li>{@code fromPosition} at or beyond the log tail — returns {@link
   *       PollRecordsResult.Success} with an empty record list and {@code nextPosition} equal to
   *       the current log tail.
   * </ul>
   *
   * @param fromPosition starting log position; use {@code -1} for oldest available
   * @param maxRecords maximum number of records to return; must be &ge; 1
   * @return a future resolving to the poll result
   */
  public ActorFuture<PollRecordsResult> pollRecords(final long fromPosition, final int maxRecords) {
    final var result = new CompletableActorFuture<PollRecordsResult>();
    actor.call(
        () -> {
          if (logStreamReader == null) {
            result.completeExceptionally(
                new IllegalStateException(
                    "Partition " + partitionId + " is not yet leader; LogStream not connected."));
            return;
          }

          if (fromPosition < 0) {
            logStreamReader.seekToFirstEvent();
          } else {
            final boolean found = logStreamReader.seek(fromPosition);
            if (!found && logStreamReader.hasNext()) {
              // The exact position was not found but there are entries after it — truncated.
              final long oldest = logStreamReader.peekNext().getPosition();
              result.complete(new PollRecordsResult.PositionTruncated(oldest));
              return;
            }
          }

          final var records = new ArrayList<PollRecord>(maxRecords);
          final var recordValue = new RawEventRecordValue();
          while (logStreamReader.hasNext() && records.size() < maxRecords) {
            final var event = logStreamReader.next();
            event.readValue(recordValue);
            final byte[] payload = new byte[recordValue.getRawPayloadLength()];
            recordValue
                .getRawPayload()
                .getBytes(recordValue.getRawPayloadOffset(), payload, 0, payload.length);
            records.add(new PollRecord(event.getPosition(), payload));
          }

          final long nextPos;
          if (!records.isEmpty()) {
            nextPos = records.get(records.size() - 1).position() + 1;
          } else {
            // Log is empty or fromPosition is at the tail — report current tail.
            final long tail = logStreamReader.seekToEnd();
            nextPos = Math.max(0L, tail);
          }

          result.complete(new PollRecordsResult.Success(records, nextPos));
        });
    return result;
  }

  private void notifyAwaiters() {
    final var snapshot = new ArrayList<>(pendingAwaiters);
    pendingAwaiters.clear();
    for (final LogRecordAwaiter awaiter : snapshot) {
      awaiter.onRecordAvailable();
    }
  }

  /**
   * Requests RAFT log compaction up to and including the RAFT entry whose highest log position is ≤
   * {@code truncateUpToPosition}. Entries at higher positions are retained.
   *
   * <p>This is a best-effort operation: if this node is not currently the leader (no RAFT server
   * connected), or if no RAFT entry maps to a position ≤ {@code truncateUpToPosition}, the call is
   * a no-op. Compaction failures are logged but do not complete the returned future exceptionally.
   *
   * <p>The compaction is scheduled on the RAFT server's internal thread context; no caller thread
   * is blocked.
   *
   * @param truncateUpToPosition log positions ≤ this value are safe to remove
   * @return a future that completes (void) when the compaction has been scheduled on the RAFT
   *     thread; it does not wait for the compaction to finish
   */
  public ActorFuture<Void> truncate(final long truncateUpToPosition) {
    final var future = new CompletableActorFuture<Void>();
    actor.call(
        () -> {
          if (raftPartitionServer == null) {
            // Not leader — nothing to compact on this node.
            future.complete(null);
            return;
          }
          final var server = raftPartitionServer;
          // Schedule the actual compaction on the RAFT thread context so that
          // LogCompactor.compact() is called from the correct thread.
          server
              .getServer()
              .getContext()
              .getThreadContext()
              .execute(
                  () -> {
                    try {
                      compactRaftLog(server, truncateUpToPosition);
                    } catch (final Exception e) {
                      LOG.warn(
                          "Truncation failed on partition {} up to position {}",
                          partitionId,
                          truncateUpToPosition,
                          e);
                    } finally {
                      future.complete(null);
                    }
                  });
        });
    return future;
  }

  /**
   * Finds the RAFT log index corresponding to {@code truncateUpToPosition} and triggers compaction.
   * Must be called on the RAFT server's thread context.
   */
  private void compactRaftLog(final RaftPartitionServer server, final long truncateUpToPosition) {
    try (final var reader = server.openReader()) {
      final long raftIndex = reader.seekToAsqn(truncateUpToPosition);
      if (raftIndex <= 0) {
        return;
      }
      // Verify the entry we seeked to actually has highestPosition <= truncateUpToPosition.
      // seekToAsqn() returns the first entry's index when no entry matches, so this check guards
      // against compacting an entry that hasn't been fully consumed yet.
      if (!reader.hasNext()) {
        return;
      }
      final var entry = reader.next();
      if (!entry.isApplicationEntry()) {
        return;
      }
      final long highestPos = entry.getApplicationEntry().highestPosition();
      if (highestPos > truncateUpToPosition) {
        // seekToAsqn() fell back to the first entry (no entry with highestPosition ≤ boundary
        // exists yet) — nothing safe to remove.
        return;
      }
      final var logCompactor = server.getServer().getContext().getLogCompactor();
      final boolean compacted = logCompactor.compactUpTo(raftIndex);
      if (compacted) {
        LOG.debug(
            "Compacted partition {} RAFT log up to index {} (position ≤ {})",
            partitionId,
            raftIndex,
            truncateUpToPosition);
      }
    }
  }

  // -------------------------------------------------------------------------
  // Result types for pollRecords

  /** A single event entry returned by {@link #pollRecords}. */
  public record PollRecord(long position, byte[] payload) {}

  /** Result of a {@link #pollRecords} call. */
  public sealed interface PollRecordsResult {

    /**
     * Records were read successfully (list may be empty when no new records exist at the requested
     * position).
     *
     * @param records the records read, in position order
     * @param nextPosition the position to pass as {@code fromPosition} on the next poll call
     */
    record Success(List<PollRecord> records, long nextPosition) implements PollRecordsResult {}

    /**
     * The requested {@code fromPosition} has been truncated; the caller should either re-subscribe
     * or resume from the reported {@code oldestAvailablePosition}.
     *
     * @param oldestAvailablePosition the oldest position still present in the log
     */
    record PositionTruncated(long oldestAvailablePosition) implements PollRecordsResult {}
  }
}
