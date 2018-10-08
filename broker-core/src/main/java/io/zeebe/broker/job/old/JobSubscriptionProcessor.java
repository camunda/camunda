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

import static io.zeebe.util.EnsureUtil.ensureGreaterThan;
import static io.zeebe.util.EnsureUtil.ensureLessThanOrEqual;
import static io.zeebe.util.EnsureUtil.ensureNotNull;
import static io.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import io.zeebe.broker.job.JobStateController;
import io.zeebe.broker.job.old.JobSubscriptions.SubscriptionIterator;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedCommandWriter;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.channel.ChannelSubscription;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.collections.Object2ObjectHashMap;

public class JobSubscriptionProcessor
    implements TypedRecordProcessor<JobRecord>, StreamProcessorLifecycleAware {
  private final Map<DirectBuffer, JobSubscriptions> typeSubscriptions =
      new Object2ObjectHashMap<>();
  private final Map<Long, JobSubscriptions> keySubscriptions = new Long2ObjectHashMap<>();
  private final CreditsRequestBuffer creditsBuffer =
      new CreditsRequestBuffer(JobSubscriptionManager.NUM_CONCURRENT_REQUESTS);
  private final JobStateController state;

  private ActorControl actor;
  private ChannelSubscription creditsSubscription;
  private TypedCommandWriter writer;

  public JobSubscriptionProcessor(JobStateController state) {
    this.state = state;
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    actor = streamProcessor.getStreamProcessorContext().getActorControl();
    creditsSubscription = actor.consume(creditsBuffer, this::consumeCreditsRequest);
    writer = streamProcessor.getEnvironment().buildCommandWriter();
  }

  @Override
  public void onClose() {
    if (creditsSubscription != null) {
      creditsSubscription.cancel();
      creditsSubscription = null;
    }
  }

  @Override
  public void processRecord(
      TypedRecord<JobRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final JobRecord job = record.getValue();
    final DirectBuffer type = job.getType();

    if (job.getRetries() < 1) {
      return;
    }

    if (typeSubscriptions.containsKey(type)) {
      final JobSubscriptions subscriptions = typeSubscriptions.get(type);
      final JobSubscription subscription = getNextAvailableSubscription(subscriptions);

      if (subscription != null) {
        activateJob(record.getKey(), job, streamWriter, subscriptions, subscription);
      }
    }
  }

  public ActorFuture<Void> addSubscription(final JobSubscription subscription) {
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
          JobSubscriptions subscriptions = typeSubscriptions.get(subscription.getJobType());
          if (subscriptions == null) {
            subscriptions = new JobSubscriptions(subscription.getJobType(), 8);
            typeSubscriptions.put(subscription.getJobType(), subscriptions);
          }

          if (!keySubscriptions.containsKey(subscription.getSubscriberKey())) {
            keySubscriptions.put(subscription.getSubscriberKey(), subscriptions);
          }

          subscriptions.addSubscription(subscription);
          activateOutstandingJobs(subscription.getJobType());
        });
  }

  public ActorFuture<Void> removeSubscription(long subscriberKey) {
    return actor.call(
        () -> {
          if (keySubscriptions.containsKey(subscriberKey)) {
            final JobSubscriptions subscriptions = keySubscriptions.get(subscriberKey);
            subscriptions.removeSubscription(subscriberKey);
            keySubscriptions.remove(subscriberKey);

            if (subscriptions.isEmpty()) {
              typeSubscriptions.remove(subscriptions.getType());
            }
          }
        });
  }

  private void activateJob(
      final long key,
      final JobRecord record,
      final TypedCommandWriter streamWriter,
      final JobSubscriptions subscriptions,
      final JobSubscription selectedSubscription) {
    final long deadline = ActorClock.currentTimeMillis() + selectedSubscription.getTimeout();

    record.setDeadline(deadline).setWorker(selectedSubscription.getWorker());

    streamWriter.writeFollowUpCommand(
        key,
        JobIntent.ACTIVATE,
        record,
        (metadata) -> {
          metadata.subscriberKey(selectedSubscription.getSubscriberKey());
          metadata.requestStreamId(selectedSubscription.getStreamId());
        });
    subscriptions.addCredits(selectedSubscription.getSubscriberKey(), -1);
  }

  public boolean increaseSubscriptionCreditsAsync(final CreditsRequest request) {
    return request.writeTo(creditsBuffer);
  }

  /**
   * When increasing the credits of a given subscription, the general strategy is to get start
   * handing out jobs in a round-robin fashion to the available subscriptions, until either:
   *
   * <ol>
   *   <li>no more jobs are activatable for this type
   *   <li>there are no subscriptions with credits available anymore
   * </ol>
   */
  private void increaseSubscriptionCredits(final CreditsRequest request) {
    final long subscriberKey = request.getSubscriberKey();
    final int credits = request.getCredits();

    if (keySubscriptions.containsKey(subscriberKey)) {
      final JobSubscriptions subscriptions = keySubscriptions.get(subscriberKey);
      subscriptions.addCredits(subscriberKey, credits);
      activateOutstandingJobs(subscriptions.getType());
    }
  }

  private void activateOutstandingJobs(final DirectBuffer type) {
    final JobSubscriptions subscriptions = typeSubscriptions.get(type);

    if (subscriptions != null) {
      for (final JobStateController.Entry entry : state.activatableJobs(type)) {
        final JobSubscription subscription = getNextAvailableSubscription(subscriptions);
        if (subscription == null) {
          break;
        }

        activateJob(entry.getKey(), entry.getRecord(), writer, subscriptions, subscription);
        writer.flush();
      }
    }
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

  private JobSubscription getNextAvailableSubscription(final JobSubscriptions subscriptions) {
    final SubscriptionIterator iterator = subscriptions.iterator();
    JobSubscription nextSubscription = null;

    if (subscriptions.getTotalCredits() > 0) {

      final int subscriptionSize = subscriptions.size();
      int seenSubscriptions = 0;

      while (seenSubscriptions < subscriptionSize && nextSubscription == null) {
        if (!iterator.hasNext()) {
          iterator.reset();
        }

        final JobSubscription subscription = iterator.next();
        if (subscription.getCredits() > 0) {
          nextSubscription = subscription;
        }

        seenSubscriptions += 1;
      }
    }
    return nextSubscription;
  }
}
