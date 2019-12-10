/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher;

import static io.zeebe.dispatcher.impl.PositionUtil.position;
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
public class DispatcherBuilder {

  private static final int DEFAULT_BUFFER_SIZE = (int) ByteValue.ofMegabytes(1).toBytes();

  private int bufferSize = -1;
  private int maxFragmentLength = -1;

  private String dispatcherName;

  private ActorScheduler actorScheduler;

  private String[] subscriptionNames;

  private int mode = Dispatcher.MODE_PUB_SUB;

  private int initialPartitionId = 0;

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
  public DispatcherBuilder bufferSize(final ByteValue byteValue) {
    bufferSize = (int) byteValue.toBytes();
    return this;
  }

  public DispatcherBuilder actorScheduler(final ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
    return this;
  }

  /** The max length of the data section of a frame */
  public DispatcherBuilder maxFragmentLength(final ByteValue maxFragmentLength) {
    this.maxFragmentLength = (int) maxFragmentLength.toBytes();
    return this;
  }

  public DispatcherBuilder initialPartitionId(final int initialPartitionId) {
    EnsureUtil.ensureGreaterThanOrEqual("initial partition id", initialPartitionId, 0);

    this.initialPartitionId = initialPartitionId;
    return this;
  }

  /**
   * Predefined subscriptions which will be created on startup in the order as they are declared.
   */
  public DispatcherBuilder subscriptions(final String... subscriptionNames) {
    this.subscriptionNames = subscriptionNames;
    return this;
  }

  /**
   * Publish-Subscribe-Mode (default): multiple subscriptions can read the same fragment / block
   * concurrently in any order.
   *
   * @see #modePipeline()
   */
  public DispatcherBuilder modePubSub() {
    mode = Dispatcher.MODE_PUB_SUB;
    return this;
  }

  /**
   * Pipeline-Mode: a subscription can only read a fragment / block if the previous subscription
   * completes reading. The subscriptions must be created on startup using the builder method {@link
   * #subscriptions(String...)} that defines the order.
   *
   * @see #modePubSub()
   */
  public DispatcherBuilder modePipeline() {
    mode = Dispatcher.MODE_PIPELINE;
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

    AtomicPosition publisherLimit = null;
    AtomicPosition publisherPosition = null;

    final long initialPosition = position(initialPartitionId, 0);

    publisherLimit = new AtomicPosition();
    publisherLimit.set(initialPosition);

    publisherPosition = new AtomicPosition();
    publisherPosition.set(initialPosition);

    // create dispatcher

    final LogBuffer logBuffer = new LogBuffer(allocatedBuffer, partitionSize, initialPartitionId);
    final LogBufferAppender logAppender = new LogBufferAppender();

    final Dispatcher dispatcher =
        new Dispatcher(
            logBuffer,
            logAppender,
            publisherLimit,
            publisherPosition,
            logWindowLength,
            maxFragmentLength,
            subscriptionNames,
            mode,
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
