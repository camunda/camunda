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
package io.zeebe.util.collection;

import java.util.function.Function;
import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;

public class ObjectPool<T> {
  protected ManyToManyConcurrentArrayQueue<T> queue;

  public ObjectPool(int capacity, Function<ObjectPool<T>, T> objectFactory) {
    this.queue = new ManyToManyConcurrentArrayQueue<>(capacity);

    for (int i = 0; i < capacity; i++) {
      this.queue.add(objectFactory.apply(this));
    }
  }

  public void returnObject(T pooledFuture) {
    queue.add(pooledFuture);
  }

  public T request() {
    return this.queue.poll();
  }
}
