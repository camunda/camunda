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
package io.zeebe.util.sched.channel;

import io.zeebe.util.sched.ActorCondition;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;

public class OneToOneRingBufferChannel extends OneToOneRingBuffer implements ConsumableChannel {
  public OneToOneRingBufferChannel(AtomicBuffer buffer) {
    super(buffer);
  }

  private final ActorConditions conditions = new ActorConditions();

  @Override
  public boolean hasAvailable() {
    return consumerPosition() < producerPosition();
  }

  @Override
  public void registerConsumer(ActorCondition onDataAvailable) {
    conditions.registerConsumer(onDataAvailable);
  }

  @Override
  public void removeConsumer(ActorCondition onDataAvailable) {
    conditions.removeConsumer(onDataAvailable);
  }

  @Override
  public boolean write(int msgTypeId, DirectBuffer srcBuffer, int srcIndex, int length) {
    try {
      return super.write(msgTypeId, srcBuffer, srcIndex, length);
    } finally {
      conditions.signalConsumers();
    }
  }
}
