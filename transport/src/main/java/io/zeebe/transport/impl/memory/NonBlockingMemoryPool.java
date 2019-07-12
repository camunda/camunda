/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.memory;

import io.zeebe.transport.Loggers;
import io.zeebe.util.ByteValue;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/**
 * Manages a fixed capacity of of memory.
 *
 * <p>Current implementation does not actually "pool" or "recycle" the memory, it leaves that to GC.
 *
 * <p>The main usecase for this pool is the zeebe broker server transports where it is not desirable
 * to block actor threads.
 */
public class NonBlockingMemoryPool implements TransportMemoryPool {
  private static final Logger LOG = Loggers.TRANSPORT_MEMORY_LOGGER;

  private final AtomicInteger remaining;

  public NonBlockingMemoryPool(int capacity) {
    this.remaining = new AtomicInteger(capacity);
  }

  public NonBlockingMemoryPool(ByteValue byteValue) {
    this((int) byteValue.toBytes());
  }

  @Override
  public ByteBuffer allocate(int requestedCapacity) {
    LOG.trace("Attempting to allocate {} bytes", requestedCapacity);

    boolean canAllocate = true;

    int current, newRemaining;

    do {
      current = remaining.get();
      newRemaining = current - requestedCapacity;
      canAllocate = newRemaining > 0;
    } while (canAllocate && !remaining.compareAndSet(current, newRemaining));

    if (canAllocate) {
      LOG.trace("Attocated {} bytes", requestedCapacity);
      return ByteBuffer.allocate(requestedCapacity);
    } else {
      LOG.trace("Failed to allocate {} bytes", requestedCapacity);
      return null;
    }
  }

  @Override
  public void reclaim(ByteBuffer buffer) {
    final int bytesReclaimed = buffer.capacity();
    LOG.trace("Reclaiming {} bytes", bytesReclaimed);
    remaining.addAndGet(bytesReclaimed);
  }
}
