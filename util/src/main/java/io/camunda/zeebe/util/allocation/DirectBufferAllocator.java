/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.allocation;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public final class DirectBufferAllocator implements BufferAllocator {
  private static final AtomicLong ALLOCATED_MEMORY = new AtomicLong();

  @Override
  public AllocatedBuffer allocate(final int capacity) {
    final AllocatedDirectBuffer buffer =
        new AllocatedDirectBuffer(
            ByteBuffer.allocateDirect(capacity), DirectBufferAllocator::onFree);

    ALLOCATED_MEMORY.addAndGet(capacity);

    return buffer;
  }

  private static void onFree(final AllocatedDirectBuffer buffer) {
    ALLOCATED_MEMORY.addAndGet(-buffer.capacity());
  }

  public static long getAllocatedMemoryInKb() {
    return ALLOCATED_MEMORY.get() / 1024;
  }
}
