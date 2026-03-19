/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.actor;

import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.eventbridge.core.logappend.RawEventRecordValue;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor responsible for reading events from the partition's {@link LogStream} and serving long-poll
 * fetch requests.
 *
 * <p>The actor starts disconnected (no log stream / reader). Once the RAFT partition for this
 * actor's partition elects a leader, {@link #connect(LogStream)} is called to open a dedicated
 * {@link LogStreamReader} and register the actor as a {@link
 * io.camunda.zeebe.logstreams.log.LogRecordAwaiter}. On role loss, {@link #disconnect()} is called.
 *
 * <h3>Long-poll semantics</h3>
 *
 * <p>When {@link #poll(long, int, int)} finds no records at {@code fromPosition} and {@code
 * serverWaitMs > 0}, the request is parked: a timeout timer is scheduled for {@code serverWaitMs}
 * (clamped to the configured ceiling) and the completing future is added to an internal list. When
 * the {@link LogStream} notifies that new records are available (via the registered {@link
 * io.camunda.zeebe.logstreams.log.LogRecordAwaiter}), all parked polls are woken and immediately
 * attempt to re-read. If the timer expires first, the poll returns an empty result.
 *
 * <h3>Thread safety</h3>
 *
 * <p>All mutable state is accessed exclusively from the actor's single-threaded work loop. The
 * {@link io.camunda.zeebe.logstreams.log.LogRecordAwaiter} callback fires on the RAFT commit
 * thread; it re-enters the actor thread via {@code actor.submit()} before touching any state.
 *
 * <h3>fromPosition semantics</h3>
 *
 * <ul>
 *   <li>{@code -1}: resolved to the oldest available position (first event in the log); if the log
 *       has been partially truncated, this is the current retention start.
 *   <li>Any value {@code < -1}: rejected with {@link InvalidPositionException} (caller maps to HTTP
 *       400).
 *   <li>Any positive value below the current oldest retained position: rejected with {@link
 *       PositionTruncatedException} carrying the oldest available position (caller maps to HTTP 400
 *       with error {@code POSITION_TRUNCATED}).
 *   <li>A value beyond the current log end: returns an empty event list with {@code nextPosition}
 *       set to the current log tail (last written position + 1, or 0 for an empty log).
 * </ul>
 */
public final class PollActor extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(PollActor.class);

  private final int partitionId;
  private final EventBridgeProperties properties;

  /** Non-null only while this node is leader. */
  private LogStream logStream;

  /** Non-null only while this node is leader. */
  private LogStreamReader reader;

  /**
   * Reusable value decoder; accessed only on this actor's thread. Avoids per-event allocation on
   * the read hot path.
   */
  private final RawEventRecordValue valueDecoder = new RawEventRecordValue();

  /** Parked long-poll requests. Accessed only on this actor's thread. */
  private final List<PendingPoll> parkedPolls = new ArrayList<>();

  /**
   * Listener registered on the {@link LogStream}; {@code onRecordAvailable()} is called from the
   * RAFT commit thread and re-enters the actor thread via {@code actor.submit()}.
   */
  private final io.camunda.zeebe.logstreams.log.LogRecordAwaiter recordAvailableListener =
      () -> actor.submit(this::wakeAllParked);

  public PollActor(final int partitionId, final EventBridgeProperties properties) {
    this.partitionId = partitionId;
    this.properties = properties;
  }

  @Override
  public String getName() {
    return "event-bridge-poll-" + partitionId;
  }

  // ---------------------------------------------------------------------------
  // Lifecycle: connect / disconnect

  /**
   * Connects this actor to a live {@link LogStream}.
   *
   * <p>Opens a dedicated {@link LogStreamReader} and registers as a {@link
   * io.camunda.zeebe.logstreams.log.LogRecordAwaiter}. If the actor was already connected (e.g. due
   * to a rapid re-election), the previous connection is torn down first.
   *
   * <p>Called by {@link io.camunda.eventbridge.broker.partition.EventBridgePartition} when this
   * node becomes the RAFT leader.
   *
   * @param stream the live log stream to connect to
   * @return a future that completes when the actor thread has applied the connection
   */
  public ActorFuture<Void> connect(final LogStream stream) {
    final var future = new CompletableActorFuture<Void>();
    actor.call(
        () -> {
          if (this.logStream != null) {
            doDisconnect();
          }
          this.logStream = stream;
          this.reader = stream.newLogStreamReader();
          stream.registerRecordAvailableListener(recordAvailableListener);
          LOG.info("Partition {} PollActor connected to LogStream", partitionId);
          future.complete(null);
        });
    return future;
  }

  /**
   * Disconnects this actor from the current {@link LogStream}.
   *
   * <p>Closes the reader, unregisters the awaiter, and fails all parked long-poll futures with an
   * {@link IllegalStateException}. After disconnect, all poll requests fail fast with a descriptive
   * error until {@link #connect(LogStream)} is called again.
   *
   * <p>Called by {@link io.camunda.eventbridge.broker.partition.EventBridgePartition} when this
   * node loses RAFT leadership.
   *
   * @return a future that completes when the actor thread has applied the disconnection
   */
  public ActorFuture<Void> disconnect() {
    final var future = new CompletableActorFuture<Void>();
    actor.call(
        () -> {
          doDisconnect();
          // Fail all parked polls so HTTP handlers can return 503 promptly.
          final var snapshot = new ArrayList<>(parkedPolls);
          parkedPolls.clear();
          for (final PendingPoll pending : snapshot) {
            pending.timer().cancel();
            pending
                .future()
                .completeExceptionally(
                    new IllegalStateException(
                        "Partition " + partitionId + " lost leadership; poll aborted."));
          }
          LOG.info("Partition {} PollActor disconnected from LogStream", partitionId);
          future.complete(null);
        });
    return future;
  }

  // ---------------------------------------------------------------------------
  // Poll

  /**
   * Fetches up to {@code maxRecords} events starting at {@code fromPosition}.
   *
   * <p>If no events are immediately available at {@code fromPosition} and {@code serverWaitMs > 0},
   * the call is parked until either a new record is committed to the log or {@code serverWaitMs}
   * milliseconds elapse (clamped to the configured long-poll ceiling).
   *
   * @param fromPosition starting position (inclusive); {@code -1} = oldest available
   * @param maxRecords maximum number of events to return; must be ≥ 1
   * @param serverWaitMs maximum server-side wait in milliseconds when no records are available;
   *     {@code 0} means return immediately; values above the configured ceiling are clamped
   * @return a future resolving to {@link PollResult}; completes exceptionally with {@link
   *     PositionTruncatedException} when {@code fromPosition} (not {@code -1}) is below the current
   *     log retention boundary, or {@link InvalidPositionException} when {@code fromPosition < -1}
   */
  public ActorFuture<PollResult> poll(
      final long fromPosition, final int maxRecords, final int serverWaitMs) {
    final var future = new CompletableActorFuture<PollResult>();
    actor.call(
        () -> {
          if (reader == null) {
            future.completeExceptionally(
                new IllegalStateException(
                    "Partition " + partitionId + " is not yet leader; LogStream not connected."));
            return;
          }
          if (fromPosition < -1L) {
            future.completeExceptionally(new InvalidPositionException(fromPosition));
            return;
          }
          final int clampedWaitMs =
              Math.min(serverWaitMs, properties.broker().longPoll().maxWaitMs());
          try {
            final PollResult result = doRead(fromPosition, maxRecords);
            if (!result.events().isEmpty() || clampedWaitMs == 0) {
              future.complete(result);
            } else {
              // No events yet and caller requested a long-poll wait — park until wake or timeout.
              parkPoll(fromPosition, maxRecords, future, clampedWaitMs);
            }
          } catch (final PositionTruncatedException | InvalidPositionException e) {
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  /**
   * Returns the current log tail position.
   *
   * <p>The tail is the position at which the next event would be written (last committed position +
   * 1, or {@code 0} for an empty log). Equivalent to the {@code position} field returned by {@code
   * GET /v1/partitions/{partitionId}/latest-position}.
   *
   * @return a future resolving to the tail position; completes exceptionally with {@link
   *     IllegalStateException} when disconnected
   */
  public ActorFuture<Long> getLatestPosition() {
    final var future = new CompletableActorFuture<Long>();
    actor.call(
        () -> {
          if (reader == null) {
            future.completeExceptionally(
                new IllegalStateException(
                    "Partition " + partitionId + " is not yet leader; LogStream not connected."));
            return;
          }
          final long lastPos = reader.seekToEnd();
          // seekToEnd() returns the last written position; the tail is one beyond that.
          // Returns 0 for an empty log (negative sentinel from seekToEnd).
          future.complete(lastPos < 0 ? 0L : lastPos + 1L);
        });
    return future;
  }

  // ---------------------------------------------------------------------------
  // Private helpers

  private void doDisconnect() {
    if (logStream != null) {
      logStream.removeRecordAvailableListener(recordAvailableListener);
      logStream = null;
    }
    if (reader != null) {
      reader.close();
      reader = null;
    }
  }

  /**
   * Reads up to {@code maxRecords} events from {@code fromPosition} (synchronously on the actor
   * thread). The reader is positioned after the last returned event on normal return.
   *
   * @throws PositionTruncatedException when {@code fromPosition} is below the oldest retained
   *     position (and is not the {@code -1} sentinel)
   */
  private PollResult doRead(final long fromPosition, final int maxRecords) {
    if (fromPosition == -1L) {
      // -1 sentinel: seek to the first available (oldest retained) position.
      reader.seekToFirstEvent();
    } else {
      final boolean found = reader.seek(fromPosition);
      if (!found) {
        if (reader.hasNext()) {
          // The reader jumped forward past fromPosition → fromPosition was truncated.
          final long oldest = reader.peekNext().getPosition();
          throw new PositionTruncatedException(oldest);
        } else {
          // fromPosition is beyond the current log end → empty response.
          return emptyResultWithTail();
        }
      }
    }

    final var events = new ArrayList<PollEvent>(Math.min(maxRecords, 256));
    while (reader.hasNext() && events.size() < maxRecords) {
      final var event = reader.next();
      event.readValue(valueDecoder);
      final byte[] payload = new byte[valueDecoder.getRawPayloadLength()];
      valueDecoder.getRawPayload().getBytes(valueDecoder.getRawPayloadOffset(), payload);
      events.add(new PollEvent(event.getPosition(), payload));
    }

    final long nextPosition;
    if (events.isEmpty()) {
      nextPosition = tailPosition();
    } else {
      nextPosition = events.get(events.size() - 1).position() + 1L;
    }
    return new PollResult(Collections.unmodifiableList(events), nextPosition);
  }

  /**
   * Returns the position at which the next event would be written: {@code seekToEnd() + 1}, or
   * {@code 0} for an empty log.
   */
  private long tailPosition() {
    final long lastPos = reader.seekToEnd();
    return lastPos < 0 ? 0L : lastPos + 1L;
  }

  /** Returns an empty {@link PollResult} with {@code nextPosition} set to the current tail. */
  private PollResult emptyResultWithTail() {
    return new PollResult(Collections.emptyList(), tailPosition());
  }

  /**
   * Parks a poll request until new records arrive or the timeout elapses.
   *
   * <p>Schedules a timer that re-reads and completes the future when it fires. Also adds the
   * request to the parked list so that {@link #wakeAllParked()} can cancel the timer and re-read
   * immediately when new records are committed.
   */
  private void parkPoll(
      final long fromPosition,
      final int maxRecords,
      final CompletableActorFuture<PollResult> future,
      final int waitMs) {
    // Use a single-element array so the timer lambda can reference the PendingPoll object
    // before it is assigned to the local variable (avoids a forward-reference problem).
    final PendingPoll[] ref = new PendingPoll[1];
    final ScheduledTimer timer =
        actor.schedule(
            Duration.ofMillis(waitMs),
            () -> {
              parkedPolls.remove(ref[0]);
              // On timer expiry: read whatever is available now (possibly still empty).
              try {
                future.complete(doRead(fromPosition, maxRecords));
              } catch (final PositionTruncatedException | InvalidPositionException e) {
                future.completeExceptionally(e);
              }
            });
    final PendingPoll pending = new PendingPoll(fromPosition, maxRecords, future, timer);
    ref[0] = pending;
    parkedPolls.add(pending);
  }

  /**
   * Wakes all parked polls, re-reads from each poll's saved {@code fromPosition}, and completes
   * each future. Called on the actor thread from the {@link
   * io.camunda.zeebe.logstreams.log.LogRecordAwaiter} callback (which uses {@code actor.submit()}
   * to re-enter).
   */
  private void wakeAllParked() {
    if (parkedPolls.isEmpty()) {
      return;
    }
    // Take a snapshot and clear the list before iterating so that recursive re-parks
    // (unlikely, but defensive) land in a fresh list.
    final var snapshot = new ArrayList<>(parkedPolls);
    parkedPolls.clear();
    for (final PendingPoll pending : snapshot) {
      pending.timer().cancel();
      try {
        pending.future().complete(doRead(pending.fromPosition(), pending.maxRecords()));
      } catch (final PositionTruncatedException | InvalidPositionException e) {
        pending.future().completeExceptionally(e);
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Public value types

  /** A single event read from the log. */
  public record PollEvent(long position, byte[] payload) {}

  /**
   * The result of a poll request.
   *
   * @param events the events read (possibly empty)
   * @param nextPosition the position to pass as {@code fromPosition} on the next poll call
   */
  public record PollResult(List<PollEvent> events, long nextPosition) {}

  // ---------------------------------------------------------------------------
  // Exception types

  /**
   * Thrown when the requested {@code fromPosition} is below the current log retention boundary. The
   * caller should surface this as HTTP 400 with error code {@code POSITION_TRUNCATED}.
   */
  public static final class PositionTruncatedException extends RuntimeException {

    private final long oldestAvailablePosition;

    public PositionTruncatedException(final long oldestAvailablePosition) {
      super(
          "Requested position has been truncated; oldest available position: "
              + oldestAvailablePosition);
      this.oldestAvailablePosition = oldestAvailablePosition;
    }

    public long getOldestAvailablePosition() {
      return oldestAvailablePosition;
    }
  }

  /**
   * Thrown when {@code fromPosition < -1}. The caller should surface this as HTTP 400 with error
   * code {@code INVALID_PARAMETER}.
   */
  public static final class InvalidPositionException extends RuntimeException {

    private final long position;

    public InvalidPositionException(final long position) {
      super("Invalid fromPosition: " + position + "; must be -1 (oldest available) or ≥ 0.");
      this.position = position;
    }

    public long getPosition() {
      return position;
    }
  }

  // ---------------------------------------------------------------------------
  // Private state types

  /** A single parked long-poll request. */
  private record PendingPoll(
      long fromPosition,
      int maxRecords,
      CompletableActorFuture<PollResult> future,
      ScheduledTimer timer) {}
}
