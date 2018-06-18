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
package io.zeebe.test.broker.protocol.clientapi;

import io.zeebe.transport.ClientInputListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;

public class RawMessageCollector implements ClientInputListener, Supplier<RawMessage> {
  protected List<RawMessage> messages = new CopyOnWriteArrayList<>();
  protected int eventToReturn = 0;

  protected Object monitor = new Object();
  protected static final long MAX_WAIT = 10 * 1000L;

  protected boolean eventsAvailable() {
    return eventToReturn < messages.size();
  }

  @Override
  public RawMessage get() {
    if (!eventsAvailable()) {
      // block haven't got enough events yet
      try {
        synchronized (monitor) {
          monitor.wait(MAX_WAIT);
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    if (!eventsAvailable()) {
      // if still not available
      throw new RuntimeException("no more events available");
    }

    final RawMessage nextMessage = messages.get(eventToReturn);
    eventToReturn++;
    return nextMessage;
  }

  public List<RawMessage> getMessages() {
    return messages;
  }

  public void moveToTail() {
    this.eventToReturn = messages.size();
  }

  public void moveToHead() {
    this.eventToReturn = 0;
  }

  public int getNumMessages() {
    return messages.size();
  }

  public long getNumMessagesFulfilling(Predicate<RawMessage> predicate) {
    return messages.stream().skip(eventToReturn).filter(predicate).count();
  }

  @Override
  public void onResponse(
      int streamId, long requestId, DirectBuffer buffer, int offset, int length) {
    messages.add(new RawMessage(true, messages.size(), buffer, offset, length));
    synchronized (monitor) {
      monitor.notifyAll();
    }
  }

  @Override
  public void onMessage(int streamId, DirectBuffer buffer, int offset, int length) {
    messages.add(new RawMessage(false, messages.size(), buffer, offset, length));
    synchronized (monitor) {
      monitor.notifyAll();
    }
  }
}
