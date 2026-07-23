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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadFactory;
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
  private final ThreadFactory threadFactory;

  // Poll-thread-only copy of the same riders FlushLoop was built with -- needed so trySeal()/
  // close() can fire SealRider#onPollBoundary/#rollbackPollBoundary here (poll thread), alongside
  // FlushLoop's own onSealed/onWindowClose/abortWindow calls (flush thread). See SealRider's own
  // javadoc for why every rider (not just poll-fed ones) is called uniformly here; the default
  // no-op covers every fold-at-flush rider.
  private final List<SealRider> riders;

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

  // See #onPollTick(long, long, Map)'s javadoc; the 2-arg overload leaves this at its empty
  // default, matching every caller that does not (yet) track Zeebe origin-position watermarks.
  private Map<Integer, Long> currentZeebeWatermarks = Map.of();

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
    this(
        config,
        segments,
        gate,
        sorter,
        encoderFactory,
        descriptorSink,
        riders,
        clock,
        meterRegistry,
        Thread::new);
  }

  /**
   * Same as the 9-arg constructor, additionally accepting the {@link ThreadFactory} {@link
   * #start()} uses to create the flush thread — e.g. to set a custom uncaught-exception handler,
   * priority, or thread group. The default (9-arg) constructor passes {@link
   * Thread#Thread(Runnable)} via {@code Thread::new}, matching this class's pre-existing behavior
   * exactly: {@link #start()} still names the thread itself afterward regardless of which factory
   * produced it.
   */
  public SinkPipeline(
      final SinkConfig config,
      final Segment[] segments,
      final BackpressureGate gate,
      final Function<Segment, SortedRun> sorter,
      final BatchEncoder.Factory encoderFactory,
      final DescriptorSink descriptorSink,
      final List<SealRider> riders,
      final LongSupplier clock,
      final MeterRegistry meterRegistry,
      final ThreadFactory threadFactory) {
    this.config = Objects.requireNonNull(config, "config");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.threadFactory = Objects.requireNonNull(threadFactory, "threadFactory");
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
    this.riders = List.copyOf(riders);
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

  /**
   * Starts the flush thread, created via the configured {@link ThreadFactory} (see this class's
   * constructors). Must be called exactly once, before any {@link #onPollTick} call.
   */
  public void start() {
    lastFlushCheckMs = clock.getAsLong();
    flushThread = threadFactory.newThread(flushLoop);
    flushThread.setName(
        "lake-sink-flush-%s-%d".formatted(config.tableName(), config.sourcePartition()));
    flushThread.start();
  }

  /**
   * Same as {@link #onPollTick(long, long, Map)}, for callers that do not track Zeebe
   * origin-position dedup watermarks — every {@link SealSnapshot} this pipeline captures carries an
   * empty {@code zeebeWatermarks} until a caller starts passing one via the 3-arg overload.
   */
  public void onPollTick(final long offsetOfLastAppendedRecord, final long frontierMs) {
    onPollTick(offsetOfLastAppendedRecord, frontierMs, Map.of());
  }

  /**
   * Called once per translator poll-loop tick, poll thread only: tracks the source offset range,
   * frontier, and Zeebe origin-position dedup watermarks (see {@code
   * io.camunda.analytics.lake.translate.LakeTranslator#watermarkSnapshot()}) covered since the last
   * descriptor, and checks the file-boundary time/size triggers against the currently-filling
   * segment. Never seals an empty segment.
   *
   * <p>{@code zeebeWatermarks} is expected to already be an immutable snapshot (see {@code
   * watermarkSnapshot()}'s own javadoc) — stored by reference, not defensively copied again, since
   * this is called once per tick, not once per record.
   *
   * <p>A no-op once {@link #isFailed()} — the translator is expected to check that itself and stop
   * feeding the pipeline, but a stray tick after failure must not attempt to touch a ring the flush
   * thread has already walked away from.
   */
  public void onPollTick(
      final long offsetOfLastAppendedRecord,
      final long frontierMs,
      final Map<Integer, Long> zeebeWatermarks) {
    if (failed) {
      return;
    }
    if (!pendingFirstOffsetSet) {
      pendingFirstOffset = offsetOfLastAppendedRecord;
      pendingFirstOffsetSet = true;
    }
    pendingLastOffset = offsetOfLastAppendedRecord;
    currentFrontierMs = frontierMs;
    currentZeebeWatermarks = zeebeWatermarks;

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
        new SealSnapshot(
            pendingFirstOffset, pendingLastOffset, currentFrontierMs, currentZeebeWatermarks);
    boundarySnapshots.addLast(snapshot);
    // Poll-fed riders must freeze their active accumulator set at this exact moment too, for the
    // exact same "publish before seal" reason as the snapshot above -- see SealRider#onPollBoundary
    // javadoc. Every rider is called uniformly; the default no-op covers every fold-at-flush rider.
    riders.forEach(SealRider::onPollBoundary);
    if (ring.seal(reason)) {
      pendingFirstOffsetSet = false;
      lastFlushCheckMs = now;
    } else {
      // ring full: the gate is already paused by ring.seal() itself; this tick's boundary attempt
      // never happened, so undo the speculative snapshot and retry on a later tick. removeLast(),
      // not remove(snapshot): nothing else appends concurrently (poll thread only), so the tail is
      // always exactly the element just added. Symmetrically, undo the speculative rider swap too
      // -- safe because nothing can run between the swap above and this rollback (poll thread only,
      // never reentrant).
      boundarySnapshots.removeLast();
      riders.forEach(SealRider::rollbackPollBoundary);
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
                currentFrontierMs,
                currentZeebeWatermarks);
        boundarySnapshots.addLast(snapshot);
        pendingFirstOffsetSet = false;
        riders.forEach(SealRider::onPollBoundary);
        while (!ring.seal(SealReason.SHUTDOWN)) {
          if (failed) {
            boundarySnapshots.removeLast();
            riders.forEach(SealRider::rollbackPollBoundary);
            break;
          }
          LockSupport.parkNanos(PARK_NANOS_WHEN_RING_FULL);
        }
      } else {
        // Nothing new to seal, but a poll-fed rider (see SealRider#onPollBoundary's javadoc) may
        // still hold state accumulated since the last boundary -- freeze it unconditionally so
        // FlushLoop's shutdown-fallback drain (see FlushLoop#drainShutdown and
        // SealRider#hasPendingPollFedData) can pick it up even though the raw window itself has
        // nothing left to close.
        riders.forEach(SealRider::onPollBoundary);
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
    return new SealSnapshot(first, pendingLastOffset, currentFrontierMs, currentZeebeWatermarks);
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
