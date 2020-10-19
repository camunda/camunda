/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher;

import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_COUNT;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.requiredCapacity;

import io.zeebe.dispatcher.impl.log.LogBuffer;
import io.zeebe.dispatcher.impl.log.LogBufferAppender;
import io.zeebe.util.ByteValue;
import io.zeebe.util.EnsureUtil;
import io.zeebe.util.allocation.AllocatedBuffer;
import io.zeebe.util.allocation.BufferAllocators;
import io.zeebe.util.sched.ActorScheduler;
import java.util.Objects;
import org.agrona.BitUtil;

/** Builder for a {@link Dispatcher} */
public final class DispatcherBuilder {

  private static final int DEFAULT_BUFFER_SIZE = (int) ByteValue.ofMegabytes(1);

  private int bufferSize = -1;
  private int maxFragmentLength = -1;

  private String dispatcherName;

  private ActorScheduler actorScheduler;

  private String[] subscriptionNames;

  private long initialPosition = 1;

  public DispatcherBuilder(final String dispatcherName) {
    this.dispatcherName = dispatcherName;
  }

  public DispatcherBuilder name(final String name) {
    dispatcherName = name;
    return this;
  }
  /**
   * The number of bytes the buffer is be able to contain. Represents the size of the data section.
   * Additional space will be allocated for the meta-data sections
   */
  public DispatcherBuilder bufferSize(final int bufferSize) {
    this.bufferSize = bufferSize;
    return this;
  }

  public DispatcherBuilder actorScheduler(final ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
    return this;
  }

  /** The max length of the data section of a frame */
  public DispatcherBuilder maxFragmentLength(final int maxFragmentLength) {
    this.maxFragmentLength = maxFragmentLength;
    return this;
  }

  public DispatcherBuilder initialPosition(final long initialPosition) {
    EnsureUtil.ensureGreaterThanOrEqual("initial position", initialPosition, 1);
    this.initialPosition = initialPosition;
    return this;
  }

  /**
   * Predefined subscriptions which will be created on startup in the order as they are declared.
   */
  public DispatcherBuilder subscriptions(final String... subscriptionNames) {
    this.subscriptionNames = subscriptionNames;
    return this;
  }

  public Dispatcher build() {
    Objects.requireNonNull(actorScheduler, "Actor scheduler cannot be null.");

    bufferSize = calculateBufferSize();
    final int partitionSize = BitUtil.align(bufferSize / PARTITION_COUNT, 8);

    // assuming that we have only a single writer, we set the frame length to max value to use as
    // much of the memory as possible
    final int logWindowLength = partitionSize / 2;
    maxFragmentLength = logWindowLength;

    final AllocatedBuffer allocatedBuffer = initAllocatedBuffer(bufferSize);

    // allocate the counters
    final AtomicPosition publisherLimit = new AtomicPosition();
    final AtomicPosition publisherPosition = new AtomicPosition();

    // create dispatcher
    final LogBuffer logBuffer = new LogBuffer(allocatedBuffer, partitionSize);
    final LogBufferAppender logAppender = new LogBufferAppender();

    final Dispatcher dispatcher =
        new Dispatcher(
            logBuffer,
            logAppender,
            publisherLimit,
            publisherPosition,
            initialPosition,
            logWindowLength,
            maxFragmentLength,
            subscriptionNames,
            dispatcherName);

    dispatcher.updatePublisherLimit(); // make subscription initially writable without waiting for
    // conductor to do this

    actorScheduler.submitActor(dispatcher);

    return dispatcher;
  }

  private int calculateBufferSize() {
    if (maxFragmentLength > 0) {
      final int partitionSize = BitUtil.align(maxFragmentLength * 2, 8);
      final int requiredBufferSize = partitionSize * PARTITION_COUNT;

      if (bufferSize > 0 && bufferSize < requiredBufferSize) {
        throw new IllegalArgumentException(
            String.format(
                "Expected the buffer size to be greater than %d, but was %d. The max fragment length is set to %d.",
                requiredBufferSize, bufferSize, maxFragmentLength));
      }

      return Math.max(bufferSize, requiredBufferSize);

    } else if (bufferSize <= 0) {
      return DEFAULT_BUFFER_SIZE;

    } else {
      return bufferSize;
    }
  }

  private AllocatedBuffer initAllocatedBuffer(final int partitionSize) {
    final int requiredCapacity = requiredCapacity(partitionSize);
    return BufferAllocators.allocateDirect(requiredCapacity);
  }
}
