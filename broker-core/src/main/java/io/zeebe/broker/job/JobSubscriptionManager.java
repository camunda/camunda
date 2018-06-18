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
package io.zeebe.broker.job;

import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.JOB_ACTIVATE_STREAM_PROCESSOR_ID;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.job.processor.ActivateJobStreamProcessor;
import io.zeebe.broker.job.processor.JobSubscription;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.TransportListener;
import io.zeebe.util.allocation.HeapBufferAllocator;
import io.zeebe.util.collection.CompactList;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.channel.ChannelSubscription;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

public class JobSubscriptionManager extends Actor implements TransportListener {
  protected static final String NAME = "jobqueue.subscription.manager";
  public static final int NUM_CONCURRENT_REQUESTS = 1_024;

  protected final StreamProcessorServiceFactory factory;
  protected final ServiceContainer serviceContext;
  private final ServerTransport transport;

  protected final Int2ObjectHashMap<PartitionBucket> logStreamBuckets = new Int2ObjectHashMap<>();
  private final Subscriptions subscriptions = new Subscriptions();
  private final Long2ObjectHashMap<ActivateJobStreamProcessor> streamProcessorBySubscriptionId =
      new Long2ObjectHashMap<>();

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

  public JobSubscriptionManager(
      ServiceContainer serviceContainer,
      StreamProcessorServiceFactory factory,
      ServerTransport transport) {
    this.transport = transport;
    this.serviceContext = serviceContainer;
    this.factory = factory;

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
  }

  @Override
  protected void onActorClosing() {
    if (creditsSubscription != null) {
      creditsSubscription.cancel();
      creditsSubscription = null;
    }
  }

  public ActorFuture<Void> addSubscription(final JobSubscription subscription) {
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    actor.call(
        () -> {
          final DirectBuffer jobType = subscription.getJobType();
          final int partitionId = subscription.getPartitionId();

          final PartitionBucket partitionBucket = logStreamBuckets.get(partitionId);
          if (partitionBucket == null) {
            future.completeExceptionally(
                new RuntimeException(
                    String.format("Partition with id '%d' not found.", partitionId)));
            return;
          }

          final long subscriptionId = nextSubscriptionId++;
          subscription.setSubscriberKey(subscriptionId);
          subscriptions.addSubscription(subscription);

          final ActivateJobStreamProcessor streamProcessor =
              partitionBucket.getActiveStreamProcessor(jobType);
          if (streamProcessor != null) {
            addSubscriptionToStreamProcessor(subscription, future, streamProcessor);
          } else {
            final ActorFuture<StreamProcessorService> openFuture =
                partitionBucket.startStreamProcessor(jobType);

            actor.runOnCompletion(
                openFuture,
                (service, t) -> {
                  final ActivateJobStreamProcessor startedStreamProcessor =
                      partitionBucket.getActiveStreamProcessor(jobType);
                  if (t == null && startedStreamProcessor != null) {
                    addSubscriptionToStreamProcessor(subscription, future, startedStreamProcessor);
                  } else {
                    subscriptions.removeSubscription(subscription.getSubscriberKey());
                    future.completeExceptionally(t);
                  }
                });
          }
        });

    return future;
  }

  private void addSubscriptionToStreamProcessor(
      final JobSubscription subscription,
      final CompletableActorFuture<Void> future,
      final ActivateJobStreamProcessor streamProcessor) {

    streamProcessorBySubscriptionId.put(subscription.getSubscriberKey(), streamProcessor);

    final ActorFuture<Void> addFuture = streamProcessor.addSubscription(subscription);
    actor.runOnCompletion(
        addFuture,
        (aVoid, throwable) -> {
          if (throwable == null) {
            future.complete(null);
          } else {
            subscriptions.removeSubscription(subscription.getSubscriberKey());
            future.completeExceptionally(throwable);
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

          final PartitionBucket partitions = logStreamBuckets.get(subscription.getPartitionId());

          final ActivateJobStreamProcessor jobStreamProcessor =
              partitions.getActiveStreamProcessor(subscription.getJobType());

          if (jobStreamProcessor != null) {
            final ActorFuture<Void> removalFuture =
                jobStreamProcessor.removeSubscription(subscriptionId);
            actor.runOnCompletion(
                removalFuture,
                (result, exception) -> {
                  if (exception == null) {
                    future.complete(null);
                  } else {
                    future.completeExceptionally(exception);
                  }

                  subscriptions.removeSubscription(subscriptionId);
                  streamProcessorBySubscriptionId.remove(subscriptionId);
                  if (subscriptions.getSubscriptionsForPartitionAndType(
                          subscription.getPartitionId(), subscription.getJobType())
                      == 0) {
                    partitions.stopStreamProcessor(subscription.getJobType());
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
    final ActivateJobStreamProcessor streamProcessor =
        streamProcessorBySubscriptionId.get(request.getSubscriberKey());

    if (streamProcessor != null) {
      return streamProcessor.increaseSubscriptionCreditsAsync(request);
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

  public void addPartition(ServiceName<Partition> partitionServiceName, Partition leaderPartition) {
    actor.call(
        () -> {
          logStreamBuckets.put(
              leaderPartition.getInfo().getPartitionId(),
              new PartitionBucket(leaderPartition, partitionServiceName));
        });
  }

  public void removePartition(Partition leaderPartition) {
    actor.call(
        () -> {
          final int partitionId = leaderPartition.getInfo().getPartitionId();
          logStreamBuckets.remove(partitionId);
          subscriptions.removeSubscriptionsForPartition(partitionId);
        });
  }

  public void onClientChannelCloseAsync(int channelId) {
    actor.call(
        () -> {
          final List<JobSubscription> affectedSubscriptions =
              subscriptions.getSubscriptionsForChannel(channelId);

          for (JobSubscription subscription : affectedSubscriptions) {
            subscriptions.removeSubscription(subscription.getSubscriberKey());

            final PartitionBucket partitions = logStreamBuckets.get(subscription.getPartitionId());

            final ActivateJobStreamProcessor streamProcessor =
                partitions.getActiveStreamProcessor(subscription.getJobType());
            streamProcessor.removeSubscription(subscription.getSubscriberKey());

            if (subscriptions.getSubscriptionsForPartitionAndType(
                    subscription.getPartitionId(), subscription.getJobType())
                == 0) {
              partitions.stopStreamProcessor(subscription.getJobType());
            }
          }
        });
  }

  @Override
  public void onConnectionEstablished(RemoteAddress remoteAddress) {}

  @Override
  public void onConnectionClosed(RemoteAddress remoteAddress) {
    onClientChannelCloseAsync(remoteAddress.getStreamId());
  }

  private static String streamProcessorName(final DirectBuffer jobType) {
    return String.format("job-activate.%s", bufferAsString(jobType));
  }

  class PartitionBucket {
    private final Partition partition;
    private final ServiceName<Partition> partitionServiceName;
    private final TypedStreamEnvironment env;

    private final Map<DirectBuffer, ActivateJobStreamProcessor> streamProcessors = new HashMap<>();
    private final Map<DirectBuffer, ActorFuture<StreamProcessorService>> openFutures =
        new HashMap<>();
    private final Map<DirectBuffer, ActorFuture<Void>> closeFutures = new HashMap<>();

    PartitionBucket(Partition partition, ServiceName<Partition> partitionServiceName) {
      this.partition = partition;
      this.partitionServiceName = partitionServiceName;
      this.env = new TypedStreamEnvironment(partition.getLogStream(), transport.getOutput());
    }

    public ActivateJobStreamProcessor getActiveStreamProcessor(DirectBuffer type) {
      if (!closeFutures.containsKey(type)) {
        return streamProcessors.get(type);
      } else {
        return null;
      }
    }

    public ActorFuture<StreamProcessorService> startStreamProcessor(DirectBuffer type) {
      if (openFutures.containsKey(type)) {
        return openFutures.get(type);
      } else {
        if (!closeFutures.containsKey(type)) {
          return createStreamProcessor(type);
        } else {
          final ActorFuture<Void> closeFuture = closeFutures.get(type);
          final CompletableActorFuture<StreamProcessorService> future =
              new CompletableActorFuture<>();

          actor.runOnCompletion(
              closeFuture,
              (closeResult, closeException) -> {
                if (closeException == null) {
                  final ActorFuture<StreamProcessorService> openFuture =
                      createStreamProcessor(type);
                  actor.runOnCompletion(
                      openFuture,
                      (openResult, openException) -> {
                        if (openException == null) {
                          future.complete(openResult);
                        } else {
                          future.completeExceptionally(openException);
                        }
                      });
                } else {
                  future.completeExceptionally(closeException);
                }
              });

          return future;
        }
      }
    }

    private ActorFuture<StreamProcessorService> createStreamProcessor(DirectBuffer type) {
      final ActivateJobStreamProcessor processor = new ActivateJobStreamProcessor(type);

      final ActorFuture<StreamProcessorService> openFuture =
          factory
              .createService(partition, partitionServiceName)
              .processor(processor.createStreamProcessor(env))
              .processorId(JOB_ACTIVATE_STREAM_PROCESSOR_ID)
              .processorName(streamProcessorName(type))
              .build();

      openFutures.put(type, openFuture);

      actor.runOnCompletion(
          openFuture,
          (result, throwable) -> {
            openFutures.remove(type);
            if (throwable == null) {
              streamProcessors.put(type, processor);
            } else {
              Loggers.SYSTEM_LOGGER.debug(
                  "Problem on starting job activating stream processor.", throwable);
            }
          });

      return openFuture;
    }

    private void stopStreamProcessor(DirectBuffer type) {
      if (closeFutures.containsKey(type)) {
        // already closing
        return;
      } else if (openFutures.containsKey(type)) {
        final ActorFuture<StreamProcessorService> openFuture = openFutures.get(type);

        actor.runOnCompletion(
            openFuture,
            (openResult, openException) -> {
              if (openException == null) {
                destroyStreamProcessor(type);
              }
              // else no need to close stream processor
            });
      } else if (streamProcessors.containsKey(type)) {
        destroyStreamProcessor(type);
      }
    }

    private ActorFuture<Void> destroyStreamProcessor(DirectBuffer type) {
      final ActorFuture<Void> closeFuture =
          serviceContext.removeService(
              LogStreamServiceNames.streamProcessorService(
                  partition.getLogStream().getLogName(), streamProcessorName(type)));
      closeFutures.put(type, closeFuture);

      actor.runOnCompletion(
          closeFuture,
          (result, throwable) -> {
            closeFutures.remove(type);
            if (throwable == null) {
              streamProcessors.remove(type);
            } else {
              Loggers.SYSTEM_LOGGER.debug(
                  "Problem on closing job activating stream processor.", throwable);
            }
          });

      return closeFuture;
    }

    public LogStream getLogStream() {
      return partition.getLogStream();
    }

    public Partition getPartition() {
      return partition;
    }

    public String getLogStreamName() {
      return partition.getLogStream().getLogName();
    }

    public ServiceName<Partition> getPartitionServiceName() {
      return partitionServiceName;
    }
  }
}
