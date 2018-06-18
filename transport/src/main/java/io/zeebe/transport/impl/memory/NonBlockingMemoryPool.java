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
