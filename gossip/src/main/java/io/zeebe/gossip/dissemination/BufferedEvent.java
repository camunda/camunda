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

import io.zeebe.util.collection.Reusable;

public class BufferedEvent<T> implements Reusable, Comparable<BufferedEvent<T>> {
  private final T event;

  private int spreadCount = 0;

  public BufferedEvent(T event) {
    this.event = event;
  }

  public T getEvent() {
    return event;
  }

  public int getSpreadCount() {
    return spreadCount;
  }

  public void incrementSpreadCount() {
    this.spreadCount += 1;
  }

  @Override
  public void reset() {
    this.spreadCount = 0;
  }

  @Override
  public int compareTo(BufferedEvent<T> o) {
    return spreadCount - o.spreadCount;
  }
}
