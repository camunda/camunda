/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.job.old;

import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.TransportListener;
import io.zeebe.util.allocation.HeapBufferAllocator;
import io.zeebe.util.collection.CompactList;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ScheduledTimer;
import io.zeebe.util.sched.channel.ChannelSubscription;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.agrona.collections.Int2ObjectHashMap;

public class JobSubscriptionManager extends Actor implements TransportListener {
  protected static final String NAME = "jobqueue.subscription.manager";
  public static final int NUM_CONCURRENT_REQUESTS = 1_024;
  private static final Duration AWAIT_PROCESSOR_TIMEOUT = Duration.ofSeconds(5);

  private final Int2ObjectHashMap<JobSubscriptionProcessor> subscriptionProcessors =
      new Int2ObjectHashMap<>();
  private final Int2ObjectHashMap<List<AwaitingSubscription>> openFutures =
      new Int2ObjectHashMap<>();
  private final Subscriptions subscriptions = new Subscriptions();

  /*
   * For credits handling, we use two datastructures here:
   *   * a one-to-one thread-safe ring buffer for ingestion of requests
   *   * a non-thread-safe list for requests that could not be successfully dispatched to the corresponding stream processor
   *
   * Note: we could also use a single data structure, if the thread-safe buffer allowed us to decide in the consuming
   *   handler whether we actually want to consume an item off of it; then, we could simply leave a request
   *   if it cannot be dispatched.
   *   afaik there is no such datastructure available out of the box, so we are going with two datastructures
   *   see also https://github.com/real-logic/Agrona/issues/96
   */
  protected final CreditsRequestBuffer creditRequestBuffer;
  protected final CompactList backPressuredCreditsRequests;
  protected final CreditsRequest creditsRequest = new CreditsRequest();

  protected long nextSubscriptionId = 0;
  private ChannelSubscription creditsSubscription;
  private ScheduledTimer futuresTimer;

  public JobSubscriptionManager() {
    this.creditRequestBuffer = new CreditsRequestBuffer(NUM_CONCURRENT_REQUESTS);
    this.backPressuredCreditsRequests =
        new CompactList(
            CreditsRequest.LENGTH,
            creditRequestBuffer.getCapacityUpperBound(),
            new HeapBufferAllocator());
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected void onActorStarted() {
    creditsSubscription = actor.consume(creditRequestBuffer, this::consumeCreditsRequest);
    futuresTimer =
        actor.runAtFixedRate(
            AWAIT_PROCESSOR_TIMEOUT,
            () -> {
              openFutures.forEach(
                  (partitionId, subscriptions) -> {
                    subscriptions
                        .stream()
                        .filter(AwaitingSubscription::isTimedOut)
                        .forEach(AwaitingSubscription::timeout);
                  });
            });
  }

  @Override
  protected void onActorClosing() {
    if (creditsSubscription != null) {
      creditsSubscription.cancel();
      creditsSubscription = null;
    }

    if (futuresTimer != null) {
      futuresTimer.cancel();
      futuresTimer = null;
    }
  }

  public ActorFuture<Void> addSubscription(final JobSubscription subscription) {
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    actor.call(
        () -> {
          final int partitionId = subscription.getPartitionId();

          final JobSubscriptionProcessor processor = subscriptionProcessors.get(partitionId);
          if (processor == null) {
            List<AwaitingSubscription> awaitingProcessor = openFutures.get(partitionId);
            if (awaitingProcessor == null) {
              awaitingProcessor = new ArrayList<>();
              openFutures.put(partitionId, awaitingProcessor);
            }

            awaitingProcessor.add(
                new AwaitingSubscription(
                    future, subscription, ActorClock.currentTimeMillis() + 5000));
            return;
          }

          registerSubscription(subscription, future, processor);
        });

    return future;
  }

  private void registerSubscription(
      JobSubscription subscription,
      CompletableActorFuture<Void> future,
      JobSubscriptionProcessor processor) {
    final long subscriptionId = nextSubscriptionId++;
    subscription.setSubscriberKey(subscriptionId);
    subscriptions.addSubscription(subscription);
    actor.runOnCompletion(
        processor.addSubscription(subscription),
        (r, t) -> {
          if (t == null) {
            future.complete(null);
          } else {
            future.completeExceptionally(t);
            subscriptions.removeSubscription(subscriptionId);
          }
        });
  }

  public ActorFuture<Void> removeSubscription(long subscriptionId) {
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    actor.call(
        () -> {
          final JobSubscription subscription = subscriptions.getSubscription(subscriptionId);

          if (subscription == null) {
            future.completeExceptionally(new RuntimeException("Subscription does not exist"));
            return;
          }

          openFutures.remove(subscription.getPartitionId());

          final JobSubscriptionProcessor processor =
              subscriptionProcessors.get(subscription.getPartitionId());
          if (processor != null) {
            final ActorFuture<Void> removalFuture = processor.removeSubscription(subscriptionId);
            actor.runOnCompletion(
                removalFuture,
                (result, exception) -> {
                  if (exception == null) {
                    future.complete(null);
                    subscriptions.removeSubscription(subscriptionId);
                  } else {
                    future.completeExceptionally(exception);
                  }
                });
          } else {
            // not caring about the case where the stream processor is still opening;
            // we assume we never receive a removal request in this situation
            future.complete(null);
          }
        });

    return future;
  }

  public boolean increaseSubscriptionCreditsAsync(CreditsRequest request) {
    return request.writeTo(creditRequestBuffer);
  }

  /**
   * @param request
   * @return if request was handled
   */
  protected boolean dispatchSubscriptionCredits(CreditsRequest request) {
    final JobSubscription subscription = subscriptions.getSubscription(request.getSubscriberKey());
    final JobSubscriptionProcessor processor =
        subscriptionProcessors.get(subscription.getPartitionId());

    if (processor != null) {
      return processor.increaseSubscriptionCreditsAsync(request);
    } else {
      // ignore
      return true;
    }
  }

  public void consumeCreditsRequest() {
    dispatchBackpressuredSubscriptionCredits();
    if (backPressuredCreditsRequests.size() == 0) {
      creditRequestBuffer.read(
          (msgTypeId, buffer, index, length) -> {
            creditsRequest.wrap(buffer, index, length);
            final boolean dispatched = dispatchSubscriptionCredits(creditsRequest);
            if (!dispatched) {
              backpressureRequest(creditsRequest);
            }
          },
          1);
    }
  }

  protected void backpressureRequest(CreditsRequest request) {
    request.appendTo(backPressuredCreditsRequests);
  }

  protected void dispatchBackpressuredSubscriptionCredits() {
    actor.runUntilDone(this::dispatchNextBackpressuredSubscriptionCredit);
  }

  protected void dispatchNextBackpressuredSubscriptionCredit() {
    final int nextRequestToConsume = backPressuredCreditsRequests.size() - 1;

    if (nextRequestToConsume >= 0) {
      creditsRequest.wrapListElement(backPressuredCreditsRequests, nextRequestToConsume);
      final boolean success = dispatchSubscriptionCredits(creditsRequest);

      if (success) {
        backPressuredCreditsRequests.remove(nextRequestToConsume);
        actor.run(this::dispatchNextBackpressuredSubscriptionCredit);
      } else {
        actor.yield();
      }
    } else {
      actor.done();
    }
  }

  public void addPartition(final int partitionId, final JobSubscriptionProcessor processor) {
    actor.call(
        () -> {
          subscriptionProcessors.put(partitionId, processor);
          if (openFutures.containsKey(partitionId)) {
            openFutures
                .get(partitionId)
                .forEach(awaitingSubscription -> awaitingSubscription.complete(processor));
            openFutures.remove(partitionId);
          }
        });
  }

  public void removePartition(final int partitionId) {
    actor.call(
        () -> {
          final List<AwaitingSubscription> removed = openFutures.remove(partitionId);
          if (removed != null) {
            removed.forEach(AwaitingSubscription::timeout);
          }

          subscriptionProcessors.remove(partitionId);
          subscriptions.removeSubscriptionsForPartition(partitionId);
        });
  }

  public void onClientChannelCloseAsync(int channelId) {
    actor.call(
        () -> {
          final List<JobSubscription> affectedSubscriptions =
              subscriptions.getSubscriptionsForChannel(channelId);

          for (JobSubscription subscription : affectedSubscriptions) {
            final JobSubscriptionProcessor processor =
                subscriptionProcessors.get(subscription.getPartitionId());
            if (processor != null) {
              processor.removeSubscription(subscription.getSubscriberKey());
            }

            subscriptions.removeSubscription(subscription.getSubscriberKey());
          }
        });
  }

  @Override
  public void onConnectionEstablished(RemoteAddress remoteAddress) {}

  @Override
  public void onConnectionClosed(RemoteAddress remoteAddress) {
    onClientChannelCloseAsync(remoteAddress.getStreamId());
  }

  private class AwaitingSubscription {
    private final CompletableActorFuture<Void> future;
    private final JobSubscription subscription;
    private final long deadline;

    AwaitingSubscription(
        CompletableActorFuture<Void> future, JobSubscription subscription, long deadline) {
      this.future = future;
      this.subscription = subscription;
      this.deadline = deadline;
    }

    boolean isTimedOut() {
      return deadline < ActorClock.currentTimeMillis();
    }

    void timeout() {
      future.completeExceptionally(
          new TimeoutException(
              String.format(
                  "timed out waiting for job stream processor for partition %d",
                  subscription.getPartitionId())));
    }

    void complete(JobSubscriptionProcessor processor) {
      registerSubscription(subscription, future, processor);
    }
  }
}
