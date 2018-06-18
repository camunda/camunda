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
package io.zeebe.gossip.dissemination;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class BufferedEventIterator<T> implements Iterator<T> {
  public static final int DEFAULT_SPREAD_LIMIT = 10;

  private final boolean incrementSpreadCount;

  private int spreadLimit = DEFAULT_SPREAD_LIMIT;

  private Iterator<BufferedEvent<T>> iterator;

  private int count = 0;
  private int limit = 0;

  public BufferedEventIterator() {
    this(false);
  }

  public BufferedEventIterator(boolean incrementSpreadCount) {
    this.incrementSpreadCount = incrementSpreadCount;
  }

  public void wrap(Iterator<BufferedEvent<T>> iterator, int limit) {
    this.iterator = iterator;
    this.limit = limit;
    this.count = 0;
  }

  public void setSpreadLimit(int spreadLimit) {
    this.spreadLimit = spreadLimit;
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext() && count < limit;
  }

  @Override
  public T next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    final BufferedEvent<T> event = iterator.next();

    count += 1;

    if (incrementSpreadCount) {
      event.incrementSpreadCount();

      if (event.getSpreadCount() >= spreadLimit) {
        iterator.remove();
      }
    }

    return event.getEvent();
  }
}
