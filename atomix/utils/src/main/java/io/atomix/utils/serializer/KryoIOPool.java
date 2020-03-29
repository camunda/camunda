/*
 * Copyright 2014-present Open Networking Foundation
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
package io.atomix.utils.serializer;

import java.lang.ref.SoftReference;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

abstract class KryoIOPool<T> {

  private final Queue<SoftReference<T>> queue = new ConcurrentLinkedQueue<>();

  private T borrow(final int bufferSize) {
    T element;
    SoftReference<T> reference;
    while ((reference = queue.poll()) != null) {
      element = reference.get();
      if (element != null) {
        return element;
      }
    }
    return create(bufferSize);
  }

  protected abstract T create(final int bufferSize);

  protected abstract boolean recycle(final T element);

  <R> R run(final Function<T, R> function, final int bufferSize) {
    final T element = borrow(bufferSize);
    try {
      return function.apply(element);
    } finally {
      if (recycle(element)) {
        queue.offer(new SoftReference<>(element));
      }
    }
  }
}
