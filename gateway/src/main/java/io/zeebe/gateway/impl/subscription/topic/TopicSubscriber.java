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
package io.zeebe.gateway.impl.subscription.topic;

import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.gateway.impl.event.TopicSubscriptionEventImpl;
import io.zeebe.gateway.impl.record.UntypedRecordImpl;
import io.zeebe.gateway.impl.subscription.Subscriber;
import io.zeebe.gateway.impl.subscription.SubscriberGroup;
import io.zeebe.gateway.impl.subscription.SubscriptionManager;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.function.Function;

public class TopicSubscriber extends Subscriber {
  private static final int MAX_HANDLING_RETRIES = 2;

  private final ZeebeClientImpl client;

  private volatile long lastProcessedEventPosition;
  private long lastAcknowledgedPosition;

  private final TopicSubscriptionSpec subscription;

  private final Function<CheckedConsumer<UntypedRecordImpl>, CheckedConsumer<UntypedRecordImpl>>
      eventHandlerAdapter;

  public TopicSubscriber(
      final ZeebeClientImpl client,
      final TopicSubscriptionSpec subscription,
      final long subscriberKey,
      final RemoteAddress eventSource,
      final int partitionId,
      final SubscriberGroup group,
      final SubscriptionManager acquisition) {
    super(
        subscriberKey, partitionId, subscription.getBufferSize(), eventSource, group, acquisition);
    this.subscription = subscription;
    this.client = client;
    this.lastProcessedEventPosition = subscription.getStartPosition(partitionId);
    this.lastAcknowledgedPosition = subscription.getStartPosition(partitionId);

    eventHandlerAdapter =
        h ->
            h.andThen(this::recordProcessedEvent)
                .andOnExceptionRetry(MAX_HANDLING_RETRIES, this::logRetry)
                .andOnException(this::logExceptionAndClose);
  }

  @Override
  public int pollEvents(final CheckedConsumer<UntypedRecordImpl> consumer) {
    return super.pollEvents(eventHandlerAdapter.apply(consumer));
  }

  protected void logExceptionAndClose(final UntypedRecordImpl event, final Exception e) {
    logEventHandlingError(e, event, "Closing subscription.");
    disable();

    acquisition.closeGroup(group, "Event handling failed");
  }

  protected void logRetry(final UntypedRecordImpl event, final Exception e) {
    logEventHandlingError(e, event, "Retrying.");
  }

  @Override
  protected ActorFuture<Void> requestSubscriptionClose() {
    LOGGER.debug("Closing subscriber at partition {}", partitionId);

    return new CloseTopicSubscriptionCommandImpl(
            client.getCommandManager(), partitionId, subscriberKey)
        .send();
  }

  @Override
  protected ActorFuture<?> requestEventSourceReplenishment(final int eventsProcessed) {
    return acknowledgeLastProcessedEvent();
  }

  protected ActorFuture<?> acknowledgeLastProcessedEvent() {

    // note: it is important we read lastProcessedEventPosition only once
    //   as it can be changed concurrently by an executor thread
    final long positionToAck = lastProcessedEventPosition;

    if (positionToAck > lastAcknowledgedPosition) {
      final ActorFuture<TopicSubscriptionEventImpl> future =
          new AcknowledgeSubscribedEventCommandImpl(client.getCommandManager(), partitionId)
              .subscriptionName(subscription.getName())
              .ackPosition(positionToAck)
              .send();

      // record this immediately to avoid repeated requests for the same position
      lastAcknowledgedPosition = positionToAck;

      return future;
    } else {
      return CompletableActorFuture.<Void>completed(null);
    }
  }

  protected void recordProcessedEvent(final UntypedRecordImpl event) {
    this.lastProcessedEventPosition = event.getMetadata().getPosition();
  }

  protected void logEventHandlingError(
      final Exception e, final UntypedRecordImpl event, final String resolution) {
    LOGGER.error(
        LOG_MESSAGE_PREFIX + "Unhandled exception during handling of event {}.{}",
        this,
        event,
        resolution,
        e);
  }

  @Override
  public String toString() {
    return "TopicSubscriber[partition="
        + partitionId
        + ", name="
        + subscription.getName()
        + ", subscriberKey="
        + subscriberKey
        + "]";
  }
}
