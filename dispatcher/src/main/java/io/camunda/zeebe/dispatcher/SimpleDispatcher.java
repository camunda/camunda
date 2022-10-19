/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dispatcher;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorCondition;
import io.camunda.zeebe.scheduler.channel.ActorConditions;
import io.camunda.zeebe.scheduler.channel.ConsumableChannel;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

public final class SimpleDispatcher extends Actor implements ConsumableChannel {
  private static final int INITIAL_POSITION = 0;

  private final ActorConditions subscriptions = new ActorConditions();
  private final Queue<ByteBuffer> entries = new ManyToOneConcurrentLinkedQueue<>();
  private final AtomicLong position;

  public SimpleDispatcher() {
    this(INITIAL_POSITION);
  }

  public SimpleDispatcher(final long initialPosition) {
    position = new AtomicLong(initialPosition);
  }

  public Claim claim(final int count) {
    return new Claim(position.getAndAdd(count));
  }

  public ByteBuffer poll() {
    return entries.poll();
  }

  private boolean offer(final ByteBuffer entry) {
    // TODO: deal with failure
    if (entries.offer(entry)) {
      subscriptions.signalConsumers();
      return true;
    }

    return false;
  }

  @Override
  public boolean hasAvailable() {
    return !entries.isEmpty();
  }

  @Override
  public void registerConsumer(final ActorCondition onDataAvailable) {
    subscriptions.registerConsumer(onDataAvailable);
  }

  @Override
  public void removeConsumer(final ActorCondition onDataAvailable) {
    subscriptions.removeConsumer(onDataAvailable);
  }

  // TODO: comment on advantage of claim type as defensive programming method
  public final class Claim {
    private final long position;

    public Claim(final long position) {
      this.position = position;
    }

    public long position() {
      return position;
    }

    public void commit(final ByteBuffer entry) {
      // TODO: deal with dispatcher being closed to avoid leaking buffers
      entries.offer(entry);
    }
  }
}
