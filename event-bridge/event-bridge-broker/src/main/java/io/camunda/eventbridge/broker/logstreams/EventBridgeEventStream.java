/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.logstreams;

import io.camunda.eventbridge.broker.flowcontrol.FlowControl;
import io.camunda.eventbridge.broker.flowcontrol.InFlightLimiter;
import io.camunda.eventbridge.broker.publish.EventStreamPublisher;
import io.camunda.eventbridge.broker.publish.InboundQueue;
import io.camunda.eventbridge.broker.watermark.HighWatermark;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.time.InstantSource;

public final class EventBridgeEventStream implements AutoCloseable {

  private static final long INITIAL_POSITION = 1L;

  private final int partitionId;
  private final LogStorage logStorage;
  private final EventStreamListener listener;
  private final ActorSchedulingService actorScheduler;
  private final InstantSource clock;
  private final int maxBatchesPerDrain;
  private final Duration lingerInterval;
  private final int queueCapacity;
  private final int maxInFlightAppends;
  private final FlowControl flowControl;
  private final HighWatermark highWatermark;

  private InboundQueue inbound;
  private EventStreamPublisher appender;
  private EventStreamWriter writer;

  public EventBridgeEventStream(final Builder builder) {
    partitionId = builder.partitionId;
    logStorage = builder.logStorage;
    listener = builder.listener;
    actorScheduler = builder.actorScheduler;
    clock = builder.clock;
    maxBatchesPerDrain = builder.maxBatchesPerDrain;
    lingerInterval = builder.lingerInterval;
    queueCapacity = builder.queueCapacity;
    maxInFlightAppends = builder.maxInFlightAppends;
    flowControl = builder.flowControl;
    highWatermark = builder.highWatermark;
  }

  public ActorFuture<Void> openAsync() {
    final var nextPosition = recoverNextPosition();

    inbound = new InboundQueue(queueCapacity, flowControl);

    appender =
        new EventStreamPublisher(
            partitionId,
            logStorage,
            listener,
            flowControl,
            nextPosition,
            clock,
            maxBatchesPerDrain,
            lingerInterval,
            maxInFlightAppends,
            inbound,
            highWatermark);

    final var submitFuture = actorScheduler.submitActor(appender);

    writer = new EventStreamWriter(inbound, appender::submitDrain);

    return submitFuture;
  }

  private long recoverNextPosition() {
    try (final var reader = newReader()) {
      final var lastPosition = reader.seekToEnd();

      if (lastPosition < 0) {
        return INITIAL_POSITION;
      }

      return lastPosition + 1;
    }
  }

  /**
   * Stops accepting new writes immediately. Already-queued entries will be drained and failed by
   * the appender during close.
   */
  public void stopWriting() {
    if (writer != null) {
      writer.close();
    }
  }

  public ActorFuture<Void> closeAsync() {
    stopWriting();
    if (appender != null) {
      final var future = appender.closeAsync();
      appender = null;
      return future;
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public void close() {
    closeAsync();
  }

  public EventStreamWriter getWriter() {
    if (writer == null) {
      throw new IllegalStateException("Event stream not open");
    }
    return writer;
  }

  public EventStreamReader newReader() {
    final var storageReader = logStorage.newReader();
    return new EventStreamReader(storageReader);
  }

  public int getPartitionId() {
    return partitionId;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private int partitionId;
    private LogStorage logStorage;
    private EventStreamListener listener;
    private ActorSchedulingService actorScheduler;
    private InstantSource clock;
    private int maxBatchesPerDrain = 256;
    private Duration lingerInterval = Duration.ofMillis(5);
    private int queueCapacity = 4096;
    private int maxInFlightAppends = 1;
    private FlowControl flowControl;
    private HighWatermark highWatermark;

    private Builder() {}

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder logStorage(final LogStorage logStorage) {
      this.logStorage = logStorage;
      return this;
    }

    public Builder listener(final EventStreamListener listener) {
      this.listener = listener;
      return this;
    }

    public Builder actorScheduler(final ActorSchedulingService actorScheduler) {
      this.actorScheduler = actorScheduler;
      return this;
    }

    public Builder clock(final InstantSource clock) {
      this.clock = clock;
      return this;
    }

    public Builder maxBatchesPerDrain(final int maxBatchesPerDrain) {
      this.maxBatchesPerDrain = maxBatchesPerDrain;
      return this;
    }

    public Builder lingerInterval(final Duration lingerInterval) {
      this.lingerInterval = lingerInterval;
      return this;
    }

    public Builder queueCapacity(final int queueCapacity) {
      this.queueCapacity = queueCapacity;
      return this;
    }

    public Builder maxInFlightAppends(final int maxInFlightAppends) {
      this.maxInFlightAppends = maxInFlightAppends;
      return this;
    }

    public Builder flowControl(final FlowControl flowControl) {
      this.flowControl = flowControl;
      return this;
    }

    public Builder highWatermark(final HighWatermark highWatermark) {
      this.highWatermark = highWatermark;
      return this;
    }

    public EventBridgeEventStream build() {
      if (logStorage == null) {
        throw new IllegalArgumentException("logStorage is required");
      }
      if (listener == null) {
        throw new IllegalArgumentException("listener is required");
      }
      if (actorScheduler == null) {
        throw new IllegalArgumentException("actorScheduler is required");
      }
      if (clock == null) {
        throw new IllegalArgumentException("clock is required");
      }
      if (flowControl == null) {
        flowControl = new InFlightLimiter(8192);
      }
      if (highWatermark == null) {
        throw new IllegalArgumentException("hight watermark is required");
      }

      return new EventBridgeEventStream(this);
    }
  }
}
