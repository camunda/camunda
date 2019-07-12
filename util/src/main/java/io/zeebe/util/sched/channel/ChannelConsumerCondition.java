/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched.channel;

import static org.agrona.UnsafeAccess.UNSAFE;

import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorJob;
import io.zeebe.util.sched.ActorSubscription;
import io.zeebe.util.sched.ActorTask;

@SuppressWarnings("restriction")
public class ChannelConsumerCondition
    implements ActorCondition, ActorSubscription, ChannelSubscription {
  private static final long TRIGGER_COUNT_OFFSET;

  private volatile long triggerCount = 0;
  private long processedTiggersCount = 0;

  private final ConsumableChannel channel;
  private final ActorJob job;
  private final ActorTask task;

  static {
    try {
      TRIGGER_COUNT_OFFSET =
          UNSAFE.objectFieldOffset(ChannelConsumerCondition.class.getDeclaredField("triggerCount"));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public ChannelConsumerCondition(ActorJob job, ConsumableChannel channel) {
    this.job = job;
    this.task = job.getTask();
    this.channel = channel;
  }

  @Override
  public boolean poll() {
    final long polledCount = this.triggerCount;
    final boolean hasAvailable = channel.hasAvailable();
    return polledCount > processedTiggersCount || hasAvailable;
  }

  @Override
  public void signal() {
    UNSAFE.getAndAddLong(this, TRIGGER_COUNT_OFFSET, 1);
    task.tryWakeup();
  }

  @Override
  public void onJobCompleted() {
    this.processedTiggersCount++;
  }

  @Override
  public ActorJob getJob() {
    return job;
  }

  @Override
  public boolean isRecurring() {
    return true;
  }

  @Override
  public void cancel() {
    channel.removeConsumer(this);
    task.onSubscriptionCancelled(this);
  }
}
