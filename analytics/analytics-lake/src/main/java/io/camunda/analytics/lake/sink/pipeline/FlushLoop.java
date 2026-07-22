/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.pipeline;

import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.ColumnarSegmentRing;
import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.Descriptor;
import io.camunda.analytics.lake.sink.DescriptorSink;
import io.camunda.analytics.lake.sink.SealReason;
import io.camunda.analytics.lake.sink.SealRider;
import io.camunda.analytics.lake.sink.Segment;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The flush thread's body: takes sealed segments off the ring, sorts them, runs seal riders, routes
 * rows into the currently open {@link FileWindow}, and closes that window into one {@link
 * Descriptor} whenever a segment is sealed for a file-boundary reason (TIME_DUE / SIZE_CAP /
 * SHUTDOWN). One instance per {@link SinkPipeline}; never touched by any other thread.
 *
 * <p><b>Release order is the load-bearing bit.</b> A SEGMENT_FULL-sealed segment is released back
 * to the ring immediately, right after its rows are copied into the still-open window — by then the
 * ring is no longer the only copy of that data (the window/encoder has it), so recycling the slot
 * is safe. The segment that triggers a file boundary is different: it is held un-released until
 * {@link DescriptorSink#accept} returns. Releasing it earlier would let the poll thread refill and
 * seal further segments while this window's commit is still in flight, so the amount of "consumed
 * but not yet durably committed" data could grow past the ring's one fixed capacity — the sink's
 * entire memory budget. Holding the triggering segment caps that float at exactly one ring's worth,
 * for exactly as long as the commit is outstanding. This is what "un-descriptored data must remain
 * replayable" means at the ring level: replay itself always starts from the last *accepted*
 * descriptor's offset stamp regardless of ring state, but bounding the float keeps the
 * fixed-memory-footprint contract true even while a commit is slow or being retried.
 */
final class FlushLoop implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(FlushLoop.class);
  private static final long PARK_NANOS_WHEN_IDLE = 1_000_000L; // 1ms

  private final SinkPipeline pipeline;
  private final ColumnarSegmentRing ring;
  private final Function<Segment, SortedRun> sorter;
  private final List<SealRider> riders;
  private final DescriptorSink descriptorSink;
  private final SinkConfig config;
  private final SinkMetrics metrics;
  private final FileWindow window;

  private volatile boolean stopRequested;

  FlushLoop(
      final SinkPipeline pipeline,
      final ColumnarSegmentRing ring,
      final Function<Segment, SortedRun> sorter,
      final BatchEncoder.Factory encoderFactory,
      final List<SealRider> riders,
      final DescriptorSink descriptorSink,
      final SinkConfig config,
      final TableSchema schema,
      final SinkMetrics metrics) {
    this.pipeline = pipeline;
    this.ring = ring;
    this.sorter = sorter;
    this.riders = riders;
    this.descriptorSink = descriptorSink;
    this.config = config;
    this.metrics = metrics;
    window = new FileWindow(encoderFactory, schema);
  }

  /** Signals the loop to stop once the ring is drained; called from {@link SinkPipeline#close}. */
  void requestStop() {
    stopRequested = true;
  }

  @Override
  public void run() {
    while (true) {
      final Segment sealed = ring.take();
      if (sealed == null) {
        if (stopRequested) {
          drainShutdown();
          return;
        }
        LockSupport.parkNanos(PARK_NANOS_WHEN_IDLE);
        continue;
      }
      try {
        processSealed(sealed);
      } catch (final RuntimeException e) {
        onFailure(e);
        return;
      }
    }
  }

  private void processSealed(final Segment sealed) {
    final SortedRun run = sorter.apply(sealed);
    for (final SealRider rider : riders) {
      rider.onSealed(run);
    }
    window.append(run);
    pipeline.updateWindowBytesEstimate(window.estimatedBytes());
    final SealReason reason = sealed.sealReason();
    metrics.seal(reason);

    if (reason == SealReason.SEGMENT_FULL) {
      // rows are already copied into the open window; the ring slot is no longer their only copy,
      // so it can be recycled without waiting on anything downstream.
      ring.release(sealed);
      return;
    }
    closeWindow(sealed, pipeline.pollBoundarySnapshot());
  }

  private void closeWindow(final Segment triggeringSegment, final SealSnapshot snapshot) {
    final Timer.Sample sample = metrics.startFlush();
    try {
      final List<DataFileResult> files = window.finishAll();
      pipeline.updateWindowBytesEstimate(0);
      final Descriptor descriptor =
          new Descriptor(
              config.tableName(),
              config.sourcePartition(),
              files,
              snapshot.firstOffset(),
              snapshot.lastOffset(),
              snapshot.frontierMs(),
              snapshot.zeebeWatermarks());
      descriptorSink.accept(descriptor);
      metrics.descriptorAccepted();
      // only now: the commit succeeded, so the segment that triggered this window can be recycled.
      if (triggeringSegment != null) {
        ring.release(triggeringSegment);
      }
    } finally {
      metrics.stopFlush(sample);
    }
  }

  /**
   * Reached only when the ring drained without ever sealing a SHUTDOWN segment (the filling segment
   * was empty when {@link SinkPipeline#close} ran) but earlier SEGMENT_FULL segments left unflushed
   * data in the still-open window — that data must still be finalized and descriptored before the
   * pipeline is allowed to stop.
   */
  private void drainShutdown() {
    if (!window.hasData()) {
      return;
    }
    try {
      closeWindow(null, pipeline.currentSnapshotForShutdownDrain());
    } catch (final RuntimeException e) {
      onFailure(e);
    }
  }

  private void onFailure(final RuntimeException e) {
    LOG.error(
        "L0 sink pipeline for table {} partition {} failed on the flush thread — aborting open "
            + "files; the pipeline is now terminal and must not be fed further rows",
        config.tableName(),
        config.sourcePartition(),
        e);
    window.abortAll();
    // any segment this failure was processing (or holding as a file-boundary trigger) is
    // deliberately left un-released: the pipeline is now terminal, so the ring is never read again
    // and the ring's fixed-memory-footprint contract no longer applies.
    pipeline.markFailed(e);
  }
}
