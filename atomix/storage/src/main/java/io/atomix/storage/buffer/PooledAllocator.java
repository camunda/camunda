/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.storage.buffer;

import io.atomix.utils.concurrent.ReferencePool;

/**
 * Pooled buffer allocator.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public abstract class PooledAllocator implements BufferAllocator {
  private final ReferencePool<AbstractBuffer> pool;

  protected PooledAllocator(final ReferencePool<AbstractBuffer> pool) {
    this.pool = pool;
  }

  /**
   * Returns the maximum buffer capacity.
   *
   * @return The maximum buffer capacity.
   */
  protected abstract int maxCapacity();

  @Override
  public Buffer allocate() {
    return allocate(4096, maxCapacity());
  }

  @Override
  public Buffer allocate(final int capacity) {
    return allocate(capacity, maxCapacity());
  }

  @Override
  public Buffer allocate(final int initialCapacity, final int maxCapacity) {
    return pool.acquire().reset(0, initialCapacity, maxCapacity).clear();
  }
}
