/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dispatcher.integration;

import io.camunda.zeebe.dispatcher.BlockPeek;
import io.camunda.zeebe.dispatcher.ClaimedFragment;
import io.camunda.zeebe.dispatcher.Dispatcher;
import io.camunda.zeebe.dispatcher.Dispatchers;
import io.camunda.zeebe.dispatcher.FragmentHandler;
import io.camunda.zeebe.dispatcher.Subscription;
import io.camunda.zeebe.util.ByteValue;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import org.agrona.DirectBuffer;
import org.junit.Rule;
import org.junit.Test;

public final class ActorFrameworkIntegrationTest {
  @Rule public final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3);

  @Test
  public void testClaim() throws InterruptedException {
    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorSchedulingService(actorSchedulerRule.get())
            .bufferSize((int) ByteValue.ofMegabytes(10))
            .build();

    actorSchedulerRule.submitActor(new Consumer(dispatcher));
    final ClaimingProducer producer = new ClaimingProducer(dispatcher);
    actorSchedulerRule.submitActor(producer);

    producer.latch.await();
    dispatcher.close();
  }

  @Test
  public void testClaimAndPeek() throws InterruptedException {
    final Dispatcher dispatcher =
        Dispatchers.create("default")
            .actorSchedulingService(actorSchedulerRule.get())
            .bufferSize((int) ByteValue.ofMegabytes(10))
            .build();

    actorSchedulerRule.submitActor(new PeekingConsumer(dispatcher));
    final ClaimingProducer producer = new ClaimingProducer(dispatcher);
    actorSchedulerRule.submitActor(producer);

    producer.latch.await();
    dispatcher.close();
  }

  class Consumer extends Actor implements FragmentHandler {
    final Dispatcher dispatcher;
    Subscription subscription;
    int counter = 0;

    Consumer(final Dispatcher dispatcher) {
      this.dispatcher = dispatcher;
    }

    @Override
    protected void onActorStarted() {
      final ActorFuture<Subscription> future =
          dispatcher.openSubscriptionAsync("consumerSubscription-" + hashCode());
      actor.runOnCompletion(
          future,
          (s, t) -> {
            subscription = s;
            actor.consume(subscription, this::consume);
          });
    }

    void consume() {
      subscription.poll(this, Integer.MAX_VALUE);
    }

    @Override
    public int onFragment(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final int streamId,
        final boolean isMarkedFailed) {
      final int newCounter = buffer.getInt(offset);
      if (newCounter - 1 != counter) {
        throw new RuntimeException(newCounter + " " + counter);
      }
      counter = newCounter;
      return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }
  }

  class PeekingConsumer extends Actor implements FragmentHandler {
    final Dispatcher dispatcher;
    final BlockPeek peek = new BlockPeek();
    Subscription subscription;
    int counter = 0;
    final Runnable processPeek = this::processPeek;

    PeekingConsumer(final Dispatcher dispatcher) {
      this.dispatcher = dispatcher;
    }

    @Override
    protected void onActorStarted() {
      final ActorFuture<Subscription> future =
          dispatcher.openSubscriptionAsync("consumerSubscription-" + hashCode());
      actor.runOnCompletion(
          future,
          (s, t) -> {
            subscription = s;
            actor.consume(subscription, this::consume);
          });
    }

    void consume() {
      if (subscription.peekBlock(peek, Integer.MAX_VALUE, true) > 0) {
        actor.runUntilDone(processPeek);
      }
    }

    void processPeek() {
      final Iterator<DirectBuffer> iterator = peek.iterator();
      while (iterator.hasNext()) {
        final DirectBuffer directBuffer = iterator.next();
        final int newCounter = directBuffer.getInt(0);
        if (newCounter - 1 != counter) {
          throw new RuntimeException(newCounter + " " + counter);
        }
        counter = newCounter;
      }
      peek.markCompleted();
      actor.done();
    }

    @Override
    public int onFragment(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final int streamId,
        final boolean isMarkedFailed) {
      final int newCounter = buffer.getInt(offset);
      if (newCounter - 1 != counter) {
        throw new RuntimeException(newCounter + " " + counter);
      }
      counter = newCounter;
      return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }
  }

  class ClaimingProducer extends Actor {
    final CountDownLatch latch = new CountDownLatch(1);

    final int totalWork = 10_000;

    final Dispatcher dispatcher;
    final ClaimedFragment claim = new ClaimedFragment();
    int counter = 1;
    final Runnable produce = this::produce;

    ClaimingProducer(final Dispatcher dispatcher) {
      this.dispatcher = dispatcher;
    }

    @Override
    protected void onActorStarted() {
      actor.run(produce);
    }

    void produce() {
      if (dispatcher.claimSingleFragment(claim, 4534) >= 0) {
        claim.getBuffer().putInt(claim.getOffset(), counter++);
        claim.commit();
      }

      if (counter < totalWork) {
        actor.yieldThread();
        actor.run(produce);
      } else {
        latch.countDown();
      }
    }
  }
}
