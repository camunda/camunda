/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.allocation;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.BufferUtil;

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
    BufferUtil.free(buffer.rawBuffer);
  }

  public static long getAllocatedMemoryInKb() {
    return ALLOCATED_MEMORY.get() / 1024;
  }
}
