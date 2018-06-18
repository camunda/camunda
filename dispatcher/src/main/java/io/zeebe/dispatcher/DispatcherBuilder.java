/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import io.zeebe.util.allocation.ExternallyAllocatedBuffer;
import io.zeebe.util.sched.ActorScheduler;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.agrona.BitUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.status.CountersManager;

/** Builder for a {@link Dispatcher} */
public class DispatcherBuilder {
  protected boolean allocateInMemory = true;

  protected ByteBuffer rawBuffer;

  protected String bufferFileName;

  protected int bufferSize = 1024 * 1024 * 1024; // default buffer size is 1 Gig

  protected int frameMaxLength;

  protected CountersManager countersManager;

  protected String dispatcherName;

  protected AtomicBuffer countersBuffer;

  protected ActorScheduler actorScheduler;

  protected String[] subscriptionNames;

  protected int mode = Dispatcher.MODE_PUB_SUB;

  protected int initialPartitionId = 0;

  public DispatcherBuilder(final String dispatcherName) {
    this.dispatcherName = dispatcherName;
  }

  public DispatcherBuilder name(final String name) {
    this.dispatcherName = name;
    return this;
  }

  /**
   * Provide a raw buffer to place the dispatcher's logbuffer in. The dispatcher will place the log
   * buffer at the beginning of the provided buffer, disregarding position and it's limit. The
   * provided buffer must be large enough to accommodate the dispatcher
   *
   * @see DispatcherBuilder#allocateInFile(String)
   */
  public DispatcherBuilder allocateInBuffer(final ByteBuffer rawBuffer) {
    this.allocateInMemory = false;
    this.rawBuffer = rawBuffer;
    return this;
  }

  /**
   * Allocate the dispatcher's buffer in the provided file by mapping it into memory. The file must
   * exist. The dispatcher will place it's buffer at the beginning of the file.
   */
  public DispatcherBuilder allocateInFile(final String fileName) {
    this.allocateInMemory = false;
    this.bufferFileName = fileName;
    return this;
  }

  /**
   * The number of bytes the buffer is be able to contain. Represents the size of the data section.
   * Additional space will be allocated for the meta-data sections
   */
  public DispatcherBuilder bufferSize(final ByteValue byteValue) {
    this.bufferSize = (int) byteValue.toBytes();
    return this;
  }

  public DispatcherBuilder actorScheduler(ActorScheduler actorScheduler) {
    this.actorScheduler = actorScheduler;
    return this;
  }

  /** The max length of the data section of a frame */
  public DispatcherBuilder frameMaxLength(final int frameMaxLength) {
    this.frameMaxLength = frameMaxLength;
    return this;
  }

  public DispatcherBuilder initialPartitionId(int initialPartitionId) {
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
    this.mode = Dispatcher.MODE_PUB_SUB;
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
    this.mode = Dispatcher.MODE_PIPELINE;
    return this;
  }

  public Dispatcher build() {
    Objects.requireNonNull(actorScheduler, "Actor scheduler cannot be null.");

    final int partitionSize = BitUtil.align(bufferSize / PARTITION_COUNT, 8);

    final AllocatedBuffer allocatedBuffer = initAllocatedBuffer(partitionSize);

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

    final int bufferWindowLength = partitionSize / 4;

    final Dispatcher dispatcher =
        new Dispatcher(
            logBuffer,
            logAppender,
            publisherLimit,
            publisherPosition,
            bufferWindowLength,
            subscriptionNames,
            mode,
            dispatcherName,
            actorScheduler.getMetricsManager());

    dispatcher.updatePublisherLimit(); // make subscription initially writable without waiting for
    // conductor to do this

    actorScheduler.submitActor(dispatcher, true);

    return dispatcher;
  }

  protected AllocatedBuffer initAllocatedBuffer(final int partitionSize) {
    final int requiredCapacity = requiredCapacity(partitionSize);

    AllocatedBuffer allocatedBuffer = null;
    if (allocateInMemory) {
      allocatedBuffer = BufferAllocators.allocateDirect(requiredCapacity);
    } else {
      if (rawBuffer != null) {
        if (rawBuffer.remaining() < requiredCapacity) {
          throw new RuntimeException("Buffer size below required capacity of " + requiredCapacity);
        }
        allocatedBuffer = new ExternallyAllocatedBuffer(rawBuffer);
      } else {
        final File bufferFile = new File(bufferFileName);
        if (!bufferFile.exists()) {
          throw new RuntimeException("File " + bufferFileName + " does not exist");
        }

        allocatedBuffer = BufferAllocators.allocateMappedFile(requiredCapacity, bufferFile);
      }
    }
    return allocatedBuffer;
  }
}
