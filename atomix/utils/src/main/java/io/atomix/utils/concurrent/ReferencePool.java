/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.utils.concurrent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Pool of reference counted objects.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class ReferencePool<T extends ReferenceCounted<?>>
    implements ReferenceManager<T>, AutoCloseable {
  private final ReferenceFactory<T> factory;
  private final Queue<T> pool = new ConcurrentLinkedQueue<>();
  private volatile boolean closed;

  public ReferencePool(final ReferenceFactory<T> factory) {
    if (factory == null) {
      throw new NullPointerException("factory cannot be null");
    }
    this.factory = factory;
  }

  /**
   * Acquires a reference.
   *
   * @return The acquired reference.
   */
  public T acquire() {
    if (closed) {
      throw new IllegalStateException("pool closed");
    }

    T reference = pool.poll();
    if (reference == null) {
      reference = factory.createReference(this);
    }
    reference.acquire();
    return reference;
  }

  @Override
  public void release(final T reference) {
    if (!closed) {
      pool.add(reference);
    }
  }

  @Override
  public synchronized void close() {
    if (closed) {
      throw new IllegalStateException("pool closed");
    }

    closed = true;
    for (final T reference : pool) {
      reference.close();
    }
  }
}
