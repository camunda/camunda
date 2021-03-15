/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.sched.channel;

import static org.agrona.UnsafeAccess.UNSAFE;

import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorJob;
import io.zeebe.util.sched.ActorSubscription;
import io.zeebe.util.sched.ActorTask;

@SuppressWarnings("restriction")
public final class ChannelConsumerCondition
    implements ActorCondition, ActorSubscription, ChannelSubscription {
  private static final long TRIGGER_COUNT_OFFSET;

  static {
    try {
      TRIGGER_COUNT_OFFSET =
          UNSAFE.objectFieldOffset(ChannelConsumerCondition.class.getDeclaredField("triggerCount"));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private final ConsumableChannel channel;
  private final ActorJob job;
  private final ActorTask task;
  private final long triggerCount = 0;
  private long processedTiggersCount = 0;

  public ChannelConsumerCondition(final ActorJob job, final ConsumableChannel channel) {
    this.job = job;
    task = job.getTask();
    this.channel = channel;
  }

  @Override
  public boolean poll() {
    final long polledCount = triggerCount;
    final boolean hasAvailable = channel.hasAvailable();
    return polledCount > processedTiggersCount || hasAvailable;
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
  public void onJobCompleted() {
    processedTiggersCount++;
  }

  @Override
  public void signal() {
    UNSAFE.getAndAddLong(this, TRIGGER_COUNT_OFFSET, 1);
    task.tryWakeup();
  }

  @Override
  public void cancel() {
    channel.removeConsumer(this);
    task.onSubscriptionCancelled(this);
  }
}
