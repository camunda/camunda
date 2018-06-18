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
package io.zeebe.util.allocation;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public class DirectBufferAllocator implements BufferAllocator {
  private static final AtomicLong ALLOCATED_MEMORY = new AtomicLong();

  @Override
  public AllocatedBuffer allocate(int capacity) {
    final AllocatedDirectBuffer buffer =
        new AllocatedDirectBuffer(
            ByteBuffer.allocateDirect(capacity), DirectBufferAllocator::onFree);

    ALLOCATED_MEMORY.addAndGet(capacity);

    return buffer;
  }

  private static void onFree(AllocatedDirectBuffer buffer) {
    ALLOCATED_MEMORY.addAndGet(-buffer.capacity());
  }

  public static long getAllocatedMemoryInKb() {
    return ALLOCATED_MEMORY.get() / 1024;
  }
}
