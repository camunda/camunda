/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.channel;

import io.zeebe.util.sched.ActorCondition;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;

public class OneToOneRingBufferChannel extends OneToOneRingBuffer implements ConsumableChannel {
  private final ActorConditions conditions = new ActorConditions();

  public OneToOneRingBufferChannel(AtomicBuffer buffer) {
    super(buffer);
  }

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
