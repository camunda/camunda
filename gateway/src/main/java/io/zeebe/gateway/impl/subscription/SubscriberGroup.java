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
package io.zeebe.gateway.impl.subscription;

import io.zeebe.gateway.cmd.ClientException;
import io.zeebe.gateway.impl.Loggers;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.gateway.impl.partitions.PartitionsRequestImpl;
import io.zeebe.gateway.impl.record.UntypedRecordImpl;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.agrona.collections.Int2ObjectHashMap;

public abstract class SubscriberGroup<T extends Subscriber> {

  protected final ActorControl actor;

  protected final ZeebeClientImpl client;
  protected final Int2ObjectHashMap<SubscriberState> subscriberState = new Int2ObjectHashMap<>();

  // thread-safe data structure for iteration by subscription executors from another thread
  protected final List<T> subscribersList = new CopyOnWriteArrayList<>();

  protected final SubscriptionManager subscriptionManager;

  protected CompletableActorFuture<SubscriberGroup<T>> openFuture;
  protected List<CompletableActorFuture<Void>> closeFutures = new ArrayList<>();

  private String closeReason;
  private Throwable closeCause;

  private volatile int state = STATE_OPENING;

  private static final int STATE_OPENING = 0;
  private static final int STATE_OPEN = 1;
  private static final int STATE_CLOSING = 2;
  private static final int STATE_CLOSED = 3;

  public SubscriberGroup(
      final ActorControl actor,
      final ZeebeClientImpl client,
      final SubscriptionManager acquisition) {
    this.actor = actor;
    this.subscriptionManager = acquisition;
    this.client = client;
  }

  protected void doAbort(final String reason, final Throwable cause) {
    setCloseReason(reason, cause);
    state = STATE_CLOSED;
    onGroupClosed();
  }

  protected void open(final CompletableActorFuture<SubscriberGroup<T>> openFuture) {
    this.openFuture = openFuture;

    final PartitionsRequestImpl partitionsRequest =
        (PartitionsRequestImpl) client.newPartitionsRequest();
    actor.runOnCompletion(
        partitionsRequest.send(),
        (partitions, failure) -> {
          if (failure != null) {
            doAbort("Requesting partitions failed", failure);
          } else {
            partitions.getPartitions().forEach(p -> openSubscriber(p.getId()));
          }
        });
  }

  public void listenForClose(final CompletableActorFuture<Void> future) {
    if (state == STATE_CLOSED) {
      future.complete(null);
    } else {
      this.closeFutures.add(future);
    }
  }

  public void initClose(final String reason, final Throwable cause) {
    if (state == STATE_OPEN) {
      state = STATE_CLOSING;
      setCloseReason(reason, null);

      // if it can be closed immediately
      final boolean nowClosed = checkGroupClosed();

      if (!nowClosed) {
        subscribersList.forEach(subscriber -> closeSubscriber(subscriber));
      }
    }

    // if state is OPENING, then closing is triggered once OPENING is reached,
  }

  private void onGroupClosed() {
    if (openFuture != null) {
      openFuture.completeExceptionally(
          new RuntimeException(
              "Could not open subscriber group " + describeGroup() + ". " + closeReason,
              closeCause));
      openFuture = null;
    }

    resolveCloseFutures();
  }

  private void resolveCloseFutures() {
    closeFutures.forEach(f -> f.complete(null));
    closeFutures.clear();
  }

  private void onGroupOpen() {
    if (allPartitionsSubscribed()) {
      if (openFuture != null) {
        openFuture.complete(this);
        openFuture = null;
      }

      if (!closeFutures.isEmpty()) {
        initClose("Close requested", null);
      }
    } else {
      // opening some subscribers failed, so we close the group again
      initClose("Could not subscribe to all partitions", null);
    }
  }

  public ActorFuture<Void> closeAsync() {
    return subscriptionManager.closeGroup(this, "Close requested");
  }

  public void reopenSubscriptionsForRemoteAsync(final RemoteAddress remoteAddress) {
    final Iterator<T> it = subscribersList.iterator();

    while (it.hasNext()) {
      final T subscriber = it.next();
      if (subscriber.getEventSource().equals(remoteAddress)) {
        subscriber.disable();
        subscribersList.remove(subscriber);
        onSubscriberClosed(subscriber);

        if (state == STATE_OPEN) {
          openSubscriber(subscriber.getPartitionId());
        }
      }
    }
  }

  public void close() {
    try {
      closeAsync().get();
    } catch (final Exception e) {
      throw new ClientException("Exception while closing subscription", e);
    }
  }

  private void openSubscriber(final int partitionId) {
    this.subscriberState.put(partitionId, SubscriberState.SUBSCRIBING);
    final ActorFuture<? extends EventSubscriptionCreationResult> future =
        requestNewSubscriber(partitionId);

    actor.runOnCompletionBlockingCurrentPhase(
        future,
        (result, throwable) -> {
          if (throwable == null) {
            onSubscriberOpened(result);
          } else {
            onSubscriberOpenFailed(partitionId, throwable);
          }
        });
  }

  private void closeSubscriber(final T subscriber) {
    subscriber.disable();
    subscribersList.remove(subscriber);
    subscriberState.put(subscriber.getPartitionId(), SubscriberState.UNSUBSCRIBING);

    actor.runUntilDone(
        () -> {
          if (!subscriber.hasEventsInProcessing()) {
            final ActorFuture<Void> closeSubscriberFuture = doCloseSubscriber(subscriber);
            actor.runOnCompletionBlockingCurrentPhase(
                closeSubscriberFuture,
                (v, t) -> {
                  if (t != null) {
                    Loggers.SUBSCRIPTION_LOGGER.error("Could not close subscriber. Ignoring.", t);
                  }

                  onSubscriberClosed(subscriber);
                });
            actor.done();
          } else {
            actor.yield();
          }
        });
  }

  protected ActorFuture<Void> doCloseSubscriber(final T subscriber) {
    return subscriber.requestSubscriptionClose();
  }

  private void setCloseReason(final String closeReason, final Throwable cause) {
    if (this.closeReason == null) {
      this.closeReason = closeReason;
      this.closeCause = cause;
    }
  }

  private void onSubscriberOpenFailed(final int partitionId, final Throwable t) {
    // TODO: exception handling => should be propagated when group is closed via checkGroupOpen
    subscriberState.put(partitionId, SubscriberState.NOT_SUBSCRIBED);

    final boolean nowOpen = checkGroupOpen();

    if (!nowOpen) {
      final boolean nowClosed = checkGroupClosed();

      if (!nowClosed && state != STATE_CLOSING) {
        initClose("Could not subscribe to partition", t);
      }
    }
  }

  public ActorCondition buildReplenishmentTrigger(final T subscriber) {
    return actor.onCondition(
        "replenishment-trigger",
        () -> {
          final ActorFuture<?> replenishmentFuture = subscriber.replenishEventSource();

          actor.runOnCompletion(
              replenishmentFuture,
              (v, t) -> {
                if (t != null) {
                  initClose("Could not replenish event source (submit ack or credits)", t);
                }
              });
        });
  }

  private void onSubscriberOpened(final EventSubscriptionCreationResult result) {
    final T subscriber = buildSubscriber(result);
    subscriberState.put(subscriber.getPartitionId(), SubscriberState.SUBSCRIBED);

    subscribersList.add(subscriber);
    subscriptionManager.addSubscriber(subscriber);

    checkGroupOpen();

    if (state == STATE_CLOSING) {
      closeSubscriber(subscriber);
    }
  }

  private void onSubscriberClosed(final T subscriber) {
    subscriptionManager.removeSubscriber(subscriber);
    subscriberState.put(subscriber.getPartitionId(), SubscriberState.NOT_SUBSCRIBED);

    checkGroupClosed();
  }

  private boolean checkGroupClosed() {
    if (state == STATE_CLOSING && allPartitionsNotSubscribed()) {
      state = STATE_CLOSED;
      onGroupClosed();
      return true;
    } else {
      return false;
    }
  }

  private boolean checkGroupOpen() {
    if (state == STATE_OPENING && allPartitionsResolved()) {
      state = STATE_OPEN;
      onGroupOpen();
      return true;
    } else {
      return false;
    }
  }

  private boolean allPartitionsSubscribed() {
    return allPartitionsInSubscriberState(s -> s == SubscriberState.SUBSCRIBED);
  }

  private boolean allPartitionsResolved() {
    return subscriberState
        .values()
        .stream()
        .allMatch(s -> s == SubscriberState.SUBSCRIBED || s == SubscriberState.NOT_SUBSCRIBED);
  }

  private boolean allPartitionsNotSubscribed() {
    return allPartitionsInSubscriberState(s -> s == SubscriberState.NOT_SUBSCRIBED);
  }

  private boolean allPartitionsInSubscriberState(final Predicate<SubscriberState> predicate) {
    return subscriberState.values().stream().allMatch(predicate);
  }

  public boolean isSubscribingTo(final int partitionId) {
    return subscriberState.get(partitionId) == SubscriberState.SUBSCRIBING;
  }

  protected abstract String describeGroup();

  public int pollEvents(final CheckedConsumer<UntypedRecordImpl> pollHandler) {
    int events = 0;
    for (final Subscriber subscriber : subscribersList) {
      events += subscriber.pollEvents(pollHandler);
    }

    return events;
  }

  public boolean isOpen() {
    return state == STATE_OPEN;
  }

  public boolean isClosed() {
    return state == STATE_CLOSED;
  }

  public int size() {
    int events = 0;
    for (final Subscriber subscriber : subscribersList) {
      events += subscriber.size();
    }

    return events;
  }

  public int numActiveSubscribers() {
    return subscribersList.size();
  }

  public abstract int poll();

  protected abstract ActorFuture<? extends EventSubscriptionCreationResult> requestNewSubscriber(
      int partitionId);

  protected abstract T buildSubscriber(EventSubscriptionCreationResult result);

  enum SubscriberState {
    NOT_SUBSCRIBED,
    UNSUBSCRIBING,
    SUBSCRIBING,
    SUBSCRIBED
  }
}
