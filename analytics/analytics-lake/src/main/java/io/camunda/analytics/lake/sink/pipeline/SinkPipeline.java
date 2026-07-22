/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.BackpressureGate;
import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.ColumnarSegmentRing;
import io.camunda.analytics.lake.sink.DescriptorSink;
import io.camunda.analytics.lake.sink.SealReason;
import io.camunda.analytics.lake.sink.SealRider;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * The per-(table x source partition) assembly and lifecycle owner of one L0 sink: constructs and
 * owns the {@link ColumnarSegmentRing}, exposes the poll-thread API ({@link #ring()}, {@link
 * #onPollTick}), and owns the flush thread ({@link #start()}/{@link #close()}).
 *
 * <p>Only two threads ever touch an instance, matching the sink package's threading-model contract:
 * whichever thread the translator's poll loop runs on calls {@link #ring()} (to build its own
 * {@code RowAppender} against, out of this package's scope) and {@link #onPollTick}; the flush
 * thread this class starts runs {@link FlushLoop} exclusively. {@link #close()} is the one
 * exception — it is expected to be called from the poll-thread side, after polling has already
 * stopped, so its own seal decision is race-free by construction (see {@link #close()}).
 *
 * <p>Time is never read via {@code System.currentTimeMillis()} directly — always through the
 * injected {@link LongSupplier}, so trigger timing is deterministic and controllable in tests.
 */
public final class SinkPipeline {

  private static final long PARK_NANOS_WHEN_RING_FULL = 1_000_000L; // 1ms

  private final SinkConfig config;
  private final ColumnarSegmentRing ring;
  private final LongSupplier clock;
  private final SinkMetrics metrics;
  private final FlushLoop flushLoop;

  // A Deque (not a Queue) is deliberate: the speculative-add/roll-back-on-failure dance in
  // trySeal()/close() must undo exactly the element it just added, by position — not by value
  // equality, which a same-valued-but-distinct SealSnapshot elsewhere in the queue could satisfy
  // instead. addLast/removeLast make the rollback unambiguous; pollFirst() is the flush thread's
  // only access, matching FIFO seal order.
  private final ConcurrentLinkedDeque<SealSnapshot> boundarySnapshots =
      new ConcurrentLinkedDeque<>();
  private final AtomicLong windowBytesEstimate = new AtomicLong();

  private Thread flushThread;
  private boolean closed;
  private volatile boolean failed;
  private volatile Throwable failureCause;

  private boolean pendingFirstOffsetSet;
  private long pendingFirstOffset;
  private long pendingLastOffset;
  private long currentFrontierMs;
  private long lastFlushCheckMs;

  public SinkPipeline(
      final SinkConfig config,
      final Segment[] segments,
      final BackpressureGate gate,
      final Function<Segment, SortedRun> sorter,
      final BatchEncoder.Factory encoderFactory,
      final DescriptorSink descriptorSink,
      final List<SealRider> riders,
      final LongSupplier clock,
      final MeterRegistry meterRegistry) {
    this.config = Objects.requireNonNull(config, "config");
    this.clock = Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(segments, "segments");
    Objects.requireNonNull(gate, "gate");
    Objects.requireNonNull(sorter, "sorter");
    Objects.requireNonNull(encoderFactory, "encoderFactory");
    Objects.requireNonNull(descriptorSink, "descriptorSink");
    Objects.requireNonNull(riders, "riders");
    Objects.requireNonNull(meterRegistry, "meterRegistry");

    if (segments.length != config.ringSegments()) {
      throw new IllegalArgumentException(
          "segments.length (%d) must equal config.ringSegments() (%d)"
              .formatted(segments.length, config.ringSegments()));
    }
    final TableSchema schema = segments[0].schema();
    for (final Segment segment : segments) {
      if (!segment.schema().equals(schema)) {
        throw new IllegalArgumentException("all segments must share the same TableSchema");
      }
    }
    if (!schema.table().equals(config.tableName())) {
      throw new IllegalArgumentException(
          "config.tableName() (%s) must equal the segments' schema table (%s)"
              .formatted(config.tableName(), schema.table()));
    }

    ring = new ColumnarSegmentRing(segments, new MeteringGate(gate));
    metrics = new SinkMetrics(meterRegistry, config.tableName(), config.sourcePartition(), ring);
    flushLoop =
        new FlushLoop(
            this,
            ring,
            sorter,
            encoderFactory,
            List.copyOf(riders),
            descriptorSink,
            config,
            schema,
            metrics);
  }

  /**
   * The poll-thread's only handle onto the ring: whatever constructs the translator's {@code
   * RowAppender} does so against this instance. Deliberately the entire poll-thread-side surface
   * beyond {@link #onPollTick} — the pipeline does not wrap or narrow it further.
   */
  public ColumnarSegmentRing ring() {
    return ring;
  }

  /** Starts the flush thread. Must be called exactly once, before any {@link #onPollTick} call. */
  public void start() {
    lastFlushCheckMs = clock.getAsLong();
    flushThread =
        new Thread(
            flushLoop,
            "lake-sink-flush-%s-%d".formatted(config.tableName(), config.sourcePartition()));
    flushThread.start();
  }

  /**
   * Called once per translator poll-loop tick, poll thread only: tracks the source offset range and
   * frontier covered since the last descriptor, and checks the file-boundary time/size triggers
   * against the currently-filling segment. Never seals an empty segment.
   *
   * <p>A no-op once {@link #isFailed()} — the translator is expected to check that itself and stop
   * feeding the pipeline, but a stray tick after failure must not attempt to touch a ring the flush
   * thread has already walked away from.
   */
  public void onPollTick(final long offsetOfLastAppendedRecord, final long frontierMs) {
    if (failed) {
      return;
    }
    if (!pendingFirstOffsetSet) {
      pendingFirstOffset = offsetOfLastAppendedRecord;
      pendingFirstOffsetSet = true;
    }
    pendingLastOffset = offsetOfLastAppendedRecord;
    currentFrontierMs = frontierMs;

    if (ring.filling().size() == 0) {
      return;
    }

    final long now = clock.getAsLong();
    if (windowBytesEstimate.get() >= config.fileTargetBytes()) {
      trySeal(SealReason.SIZE_CAP, now);
    } else if (now - lastFlushCheckMs >= config.flushIntervalMs()) {
      trySeal(SealReason.TIME_DUE, now);
    }
  }

  private void trySeal(final SealReason reason, final long now) {
    // The snapshot must be published (into a structure the flush thread will read) strictly
    // before the seal's volatile head++ store, or the flush thread could observe the sealed
    // segment and poll an empty queue: enqueue first, then seal, then roll back on failure.
    final SealSnapshot snapshot =
        new SealSnapshot(pendingFirstOffset, pendingLastOffset, currentFrontierMs);
    boundarySnapshots.addLast(snapshot);
    if (ring.seal(reason)) {
      pendingFirstOffsetSet = false;
      lastFlushCheckMs = now;
    } else {
      // ring full: the gate is already paused by ring.seal() itself; this tick's boundary attempt
      // never happened, so undo the speculative snapshot and retry on a later tick. removeLast(),
      // not remove(snapshot): nothing else appends concurrently (poll thread only), so the tail is
      // always exactly the element just added.
      boundarySnapshots.removeLast();
    }
  }

  /**
   * Drains: seals whatever the filling segment holds with SHUTDOWN (blocking until the ring has
   * room, if it doesn't already), then waits for the flush thread to finish flushing everything —
   * including any window still open with no fresh boundary segment — and finalize files, emit the
   * last descriptor, and stop. Idempotent; safe to call on a pipeline that never received a row.
   */
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    if (!failed) {
      final Segment filling = ring.filling();
      if (filling.size() > 0) {
        final SealSnapshot snapshot =
            new SealSnapshot(
                pendingFirstOffsetSet ? pendingFirstOffset : pendingLastOffset,
                pendingLastOffset,
                currentFrontierMs);
        boundarySnapshots.addLast(snapshot);
        pendingFirstOffsetSet = false;
        while (!ring.seal(SealReason.SHUTDOWN)) {
          if (failed) {
            boundarySnapshots.removeLast();
            break;
          }
          LockSupport.parkNanos(PARK_NANOS_WHEN_RING_FULL);
        }
      }
    }
    flushLoop.requestStop();
    try {
      flushThread.join();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while closing the L0 sink pipeline", e);
    }
  }

  /** Whether the pipeline has hit a terminal failure; the translator must check this and stop. */
  public boolean isFailed() {
    return failed;
  }

  /** The cause of the terminal failure, or {@code null} if {@link #isFailed()} is false. */
  public Throwable failureCause() {
    return failureCause;
  }

  // ---- package-private wiring for FlushLoop -------------------------------------------------

  void updateWindowBytesEstimate(final long value) {
    windowBytesEstimate.set(value);
  }

  /** Consumes the snapshot matching the next non-SEGMENT_FULL sealed segment, in seal order. */
  SealSnapshot pollBoundarySnapshot() {
    return boundarySnapshots.pollFirst();
  }

  /**
   * Fallback for the shutdown-drain case where the flush thread finds leftover appended-but-
   * unclosed window data with no boundary segment behind it (the filling segment was empty at
   * {@link #close()} time, so no SHUTDOWN seal — hence no queued snapshot — was ever produced).
   * Safe to read without synchronization: by the time the flush thread reaches this, {@link
   * #close()} guarantees no further {@link #onPollTick} calls are in flight.
   */
  SealSnapshot currentSnapshotForShutdownDrain() {
    final long first = pendingFirstOffsetSet ? pendingFirstOffset : pendingLastOffset;
    return new SealSnapshot(first, pendingLastOffset, currentFrontierMs);
  }

  void markFailed(final Throwable cause) {
    failureCause = cause;
    failed = true;
    metrics.markFailed();
  }

  /** Wraps the injected gate so pause/resume calls are also counted, without changing behavior. */
  private final class MeteringGate implements BackpressureGate {

    private final BackpressureGate delegate;

    private MeteringGate(final BackpressureGate delegate) {
      this.delegate = delegate;
    }

    @Override
    public void pause() {
      delegate.pause();
      metrics.backpressurePaused();
    }

    @Override
    public void resume() {
      delegate.resume();
      metrics.backpressureResumed();
    }
  }
}
