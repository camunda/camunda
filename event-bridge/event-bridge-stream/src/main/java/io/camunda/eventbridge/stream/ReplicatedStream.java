/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorListener;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;
import java.util.List;
import java.util.function.Supplier;

/**
 * A replicated state engine over a Raft partition's log: a Zeebe {@link StreamProcessor} backed by
 * a {@link ZeebeDb}. Commands are written with {@link #writer}; the registered {@link
 * RecordProcessor}s apply them and emit committed events. A <b>leader</b> runs in {@link
 * StreamProcessorMode#PROCESSING} and accepts writes; a <b>follower / passive observer</b> runs in
 * {@link StreamProcessorMode#REPLAY} and replays committed events into its own state, so every
 * replica converges and a new leader resumes without loss. Snapshotting is layered on separately by
 * the partition lifecycle.
 *
 * <p>Subclasses supply the partition-specific pieces — the log name, the {@link RecordValues}
 * deserializer, and the {@link RecordProcessor}s — and add their own typed write/read methods over
 * {@link #writer} and the state they create in {@link #onStarting()}.
 *
 * @param <C> the column-family enum of the backing {@link ZeebeDb}
 */
public abstract class ReplicatedStream<C extends Enum<? extends EnumValue> & EnumValue> {

  protected final int partitionId;
  protected final ZeebeDb<C> zeebeDb;

  private final LogStorage logStorage;
  private final ActorSchedulingService actorScheduler;
  private final InstantSource clock;
  private final MeterRegistry meterRegistry;

  private LogStream logStream;
  private StreamProcessor streamProcessor;

  /** The log writer; valid only after {@link #start} completes. Used by subclass write methods. */
  protected LogStreamWriter writer;

  protected ReplicatedStream(
      final int partitionId,
      final LogStorage logStorage,
      final ActorSchedulingService actorScheduler,
      final ZeebeDb<C> zeebeDb,
      final InstantSource clock,
      final MeterRegistry meterRegistry) {
    this.partitionId = partitionId;
    this.logStorage = logStorage;
    this.actorScheduler = actorScheduler;
    this.zeebeDb = zeebeDb;
    this.clock = clock;
    this.meterRegistry = meterRegistry;
  }

  /**
   * The log name prefix (the partition id is appended), e.g. {@code "metadata"} or {@code
   * "coordinator"}.
   */
  protected abstract String logName();

  /** The {@link RecordValues} deserializer for this stream's record types. */
  protected abstract Supplier<RecordValues> recordValues();

  /**
   * The processors to register, built after {@link #onStarting()} so they can capture the state it
   * creates.
   */
  protected abstract List<RecordProcessor> createProcessors();

  /**
   * Hook invoked after the log stream is built but before the processors are created — the place to
   * create the state DB views (and seed any caches) the processors will use. Default: no-op.
   */
  protected void onStarting() {}

  /** An optional processing listener (e.g. to correlate a written command to a caller). */
  protected StreamProcessorListener listener() {
    return null;
  }

  /**
   * Starts the stream processor in the given mode. The returned future completes once the processor
   * is <em>opened</em> — which is before the log has been replayed. To run logic against fully
   * replayed state, register an {@code onRecovered} callback: it fires after replay completes and
   * before processing begins, and only in {@link StreamProcessorMode#PROCESSING} (followers stay in
   * replay and never invoke it).
   *
   * @param onRecovered invoked on the processor's actor thread once replay has finished; may be
   *     {@code null}
   */
  public ActorFuture<Void> start(final StreamProcessorMode mode, final Runnable onRecovered) {
    logStream =
        LogStream.builder()
            .withLogStorage(logStorage)
            .withLogName(logName() + "-" + partitionId)
            .withPartitionId(partitionId)
            .withClock(clock)
            .withMeterRegistry(meterRegistry)
            .build();

    onStarting();

    final var builder =
        StreamProcessor.builder()
            .meterRegistry(meterRegistry)
            .clock(StreamClock.controllable(clock))
            .logStream(logStream)
            .zeebeDb(zeebeDb)
            .actorSchedulingService(actorScheduler)
            .recordProcessors(createProcessors())
            .recordValues(recordValues())
            .commandResponseWriter(new NoopCommandResponseWriter())
            .partitionCommandSender(new NoopInterPartitionCommandSender())
            .streamProcessorMode(mode);

    final var processingListener = listener();
    if (processingListener != null) {
      builder.listener(processingListener);
    }

    if (onRecovered != null) {
      builder.addLifecycleListener(
          new StreamProcessorLifecycleAware() {
            @Override
            public void onRecovered(final ReadonlyStreamProcessorContext context) {
              onRecovered.run();
            }
          });
    }

    streamProcessor = builder.build();
    writer = logStream.newLogStreamWriter();
    return streamProcessor.openAsync(false);
  }

  /** The underlying stream processor, e.g. for the snapshot director. */
  public final StreamProcessor streamProcessor() {
    return streamProcessor;
  }

  /** The log stream, e.g. to seed the snapshot director's commit position from the log tip. */
  public final LogStream logStream() {
    return logStream;
  }

  /**
   * Stops the processor and log stream. The {@code ZeebeDb} is owned/closed by the StateController.
   */
  public ActorFuture<Void> stop() {
    final ActorFuture<Void> closed = streamProcessor.closeAsync();
    closed.onComplete(
        (ok, error) -> {
          if (logStream != null) {
            logStream.close();
          }
        });
    return closed;
  }
}
