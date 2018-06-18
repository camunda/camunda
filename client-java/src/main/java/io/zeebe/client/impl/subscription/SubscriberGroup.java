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
package io.zeebe.client.impl.subscription;

import io.zeebe.client.api.commands.Partition;
import io.zeebe.client.api.commands.Topic;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.Loggers;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.record.UntypedRecordImpl;
import io.zeebe.client.impl.topic.TopicsRequestImpl;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.agrona.collections.Int2ObjectHashMap;

public abstract class SubscriberGroup<T extends Subscriber> {

  protected final ActorControl actor;

  protected final ZeebeClientImpl client;
  protected final Int2ObjectHashMap<SubscriberState> subscriberState = new Int2ObjectHashMap<>();

  // thread-safe data structure for iteration by subscription executors from another thread
  protected final List<T> subscribersList = new CopyOnWriteArrayList<>();

  protected final String topic;
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
      ActorControl actor, ZeebeClientImpl client, SubscriptionManager acquisition, String topic) {
    this.actor = actor;
    this.subscriptionManager = acquisition;
    this.client = client;
    this.topic = topic;
  }

  protected void doAbort(String reason, Throwable cause) {
    setCloseReason(reason, cause);
    state = STATE_CLOSED;
    onGroupClosed();
  }

  protected void open(CompletableActorFuture<SubscriberGroup<T>> openFuture) {
    this.openFuture = openFuture;

    final TopicsRequestImpl topicsRequest = (TopicsRequestImpl) client.newTopicsRequest();
    actor.runOnCompletion(
        topicsRequest.send(),
        (topics, failure) -> {
          if (failure != null) {
            doAbort("Requesting partitions failed", failure);
          } else {
            final Optional<Topic> requestedTopic =
                topics.getTopics().stream().filter(t -> topic.equals(t.getName())).findFirst();

            if (requestedTopic.isPresent()) {
              final List<Partition> partitions = requestedTopic.get().getPartitions();

              partitions.forEach(p -> openSubscriber(p.getId()));
            } else {
              doAbort("Topic " + topic + " is not known", null);
            }
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

  public void initClose(String reason, Throwable cause) {
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

  public void reopenSubscriptionsForRemoteAsync(RemoteAddress remoteAddress) {
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
    } catch (Exception e) {
      throw new ClientException("Exception while closing subscription", e);
    }
  }

  private void openSubscriber(int partitionId) {
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

  private void closeSubscriber(T subscriber) {
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

  protected ActorFuture<Void> doCloseSubscriber(T subscriber) {
    return subscriber.requestSubscriptionClose();
  }

  private void setCloseReason(String closeReason, Throwable cause) {
    if (this.closeReason == null) {
      this.closeReason = closeReason;
      this.closeCause = cause;
    }
  }

  private void onSubscriberOpenFailed(int partitionId, Throwable t) {
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

  public ActorCondition buildReplenishmentTrigger(T subscriber) {
    return actor.onCondition(
        topic,
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

  private void onSubscriberOpened(EventSubscriptionCreationResult result) {
    final T subscriber = buildSubscriber(result);
    subscriberState.put(subscriber.getPartitionId(), SubscriberState.SUBSCRIBED);

    subscribersList.add(subscriber);
    subscriptionManager.addSubscriber(subscriber);

    checkGroupOpen();

    if (state == STATE_CLOSING) {
      closeSubscriber(subscriber);
    }
  }

  private void onSubscriberClosed(T subscriber) {
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

  private boolean allPartitionsInSubscriberState(Predicate<SubscriberState> predicate) {
    return subscriberState.values().stream().allMatch(predicate);
  }

  public boolean isSubscribingTo(int partitionId) {
    return subscriberState.get(partitionId) == SubscriberState.SUBSCRIBING;
  }

  protected abstract String describeGroup();

  public int pollEvents(CheckedConsumer<UntypedRecordImpl> pollHandler) {
    int events = 0;
    for (Subscriber subscriber : subscribersList) {
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
    for (Subscriber subscriber : subscribersList) {
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
    SUBSCRIBED;
  }
}
