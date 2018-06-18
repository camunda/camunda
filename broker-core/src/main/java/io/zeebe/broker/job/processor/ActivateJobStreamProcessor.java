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
package io.zeebe.broker.job.processor;

import static io.zeebe.util.EnsureUtil.*;

import io.zeebe.broker.job.CreditsRequest;
import io.zeebe.broker.job.CreditsRequestBuffer;
import io.zeebe.broker.job.JobSubscriptionManager;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.job.processor.JobSubscriptions.SubscriptionIterator;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.channel.ChannelSubscription;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;

public class ActivateJobStreamProcessor
    implements TypedRecordProcessor<JobRecord>, StreamProcessorLifecycleAware {
  protected final CreditsRequestBuffer creditsBuffer =
      new CreditsRequestBuffer(JobSubscriptionManager.NUM_CONCURRENT_REQUESTS);

  private final JobSubscriptions subscriptions = new JobSubscriptions(8);
  private final SubscriptionIterator jobDistributionIterator;

  private final DirectBuffer subscribedJobType;
  private ActorControl actor;
  private StreamProcessorContext context;

  private JobSubscription selectedSubscriber;
  private ChannelSubscription creditsSubscription;

  public ActivateJobStreamProcessor(DirectBuffer jobType) {
    this.subscribedJobType = jobType;
    this.jobDistributionIterator = subscriptions.iterator();
  }

  public DirectBuffer getSubscriptedJobType() {
    return subscribedJobType;
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    this.context = streamProcessor.getStreamProcessorContext();
    this.actor = context.getActorControl();
    creditsSubscription = actor.consume(creditsBuffer, this::consumeCreditsRequest);

    // activate the processor while adding the first subscription
    context.suspendController();
  }

  @Override
  public void onClose() {
    if (creditsSubscription != null) {
      creditsSubscription.cancel();
      creditsSubscription = null;
    }
  }

  public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment env) {
    return env.newStreamProcessor()
        .onEvent(ValueType.JOB, JobIntent.CREATED, this)
        .onEvent(ValueType.JOB, JobIntent.TIMED_OUT, this)
        .onEvent(ValueType.JOB, JobIntent.FAILED, this)
        .onEvent(ValueType.JOB, JobIntent.RETRIES_UPDATED, this)
        .build();
  }

  public ActorFuture<Void> addSubscription(JobSubscription subscription) {
    try {
      ensureNotNull("subscription", subscription);
      ensureNotNullOrEmpty("job type", subscription.getJobType());
      ensureNotNullOrEmpty("worker", subscription.getWorker());
      ensureGreaterThan("length of worker", subscription.getWorker().capacity(), 0);
      ensureLessThanOrEqual(
          "length of worker",
          subscription.getWorker().capacity(),
          JobSubscription.WORKER_MAX_LENGTH);
      ensureGreaterThan("timeout", subscription.getTimeout(), 0);
      ensureGreaterThan("subscription credits", subscription.getCredits(), 0);
    } catch (Exception e) {
      return CompletableActorFuture.completedExceptionally(e);
    }

    return actor.call(
        () -> {
          subscriptions.addSubscription(subscription);

          context.resumeController();
        });
  }

  public ActorFuture<Void> removeSubscription(long subscriberKey) {
    return actor.call(
        () -> {
          subscriptions.removeSubscription(subscriberKey);
          final boolean isSuspended = subscriptions.isEmpty();
          if (isSuspended) {
            context.suspendController();
          }
        });
  }

  private void consumeCreditsRequest() {
    final CreditsRequest creditsRequest = new CreditsRequest();
    creditsBuffer.read(
        (msgTypeId, buffer, index, length) -> {
          creditsRequest.wrap(buffer, index, length);
          increaseSubscriptionCredits(creditsRequest);
        },
        1);
  }

  public boolean increaseSubscriptionCreditsAsync(CreditsRequest request) {
    return request.writeTo(creditsBuffer);
  }

  protected void increaseSubscriptionCredits(CreditsRequest request) {
    final long subscriberKey = request.getSubscriberKey();
    final int credits = request.getCredits();

    subscriptions.addCredits(subscriberKey, credits);

    context.resumeController();
  }

  protected JobSubscription getNextAvailableSubscription() {
    JobSubscription nextSubscription = null;

    if (subscriptions.getTotalCredits() > 0) {

      final int subscriptionSize = subscriptions.size();
      int seenSubscriptions = 0;

      while (seenSubscriptions < subscriptionSize && nextSubscription == null) {
        if (!jobDistributionIterator.hasNext()) {
          jobDistributionIterator.reset();
        }

        final JobSubscription subscription = jobDistributionIterator.next();
        if (subscription.getCredits() > 0) {
          nextSubscription = subscription;
        }

        seenSubscriptions += 1;
      }
    }
    return nextSubscription;
  }

  @Override
  public void processRecord(TypedRecord<JobRecord> event) {
    selectedSubscriber = null;

    final JobRecord jobEvent = event.getValue();
    final boolean handlesJobType = BufferUtil.equals(jobEvent.getType(), subscribedJobType);

    if (handlesJobType && jobEvent.getRetries() > 0) {
      selectedSubscriber = getNextAvailableSubscription();
      if (selectedSubscriber != null) {
        final long deadline = ActorClock.currentTimeMillis() + selectedSubscriber.getTimeout();

        jobEvent.setDeadline(deadline).setWorker(selectedSubscriber.getWorker());
      }
    }
  }

  @Override
  public long writeRecord(TypedRecord<JobRecord> event, TypedStreamWriter writer) {
    long position = 0;

    if (selectedSubscriber != null) {
      position =
          writer.writeFollowUpCommand(
              event.getKey(),
              JobIntent.ACTIVATE,
              event.getValue(),
              this::assignToSelectedSubscriber);
    }
    return position;
  }

  private void assignToSelectedSubscriber(RecordMetadata metadata) {
    metadata.subscriberKey(selectedSubscriber.getSubscriberKey());
    metadata.requestStreamId(selectedSubscriber.getStreamId());
  }

  @Override
  public void updateState(TypedRecord<JobRecord> event) {
    if (selectedSubscriber != null) {
      subscriptions.addCredits(selectedSubscriber.getSubscriberKey(), -1);

      if (subscriptions.getTotalCredits() <= 0) {
        context.suspendController();
      }
    }
  }
}
