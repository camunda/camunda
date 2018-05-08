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

import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.JOB_LOCK_STREAM_PROCESSOR_ID;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.*;
import java.util.Map.Entry;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.job.processor.LockJobStreamProcessor;
import io.zeebe.broker.job.processor.JobSubscription;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.transport.*;
import io.zeebe.util.allocation.HeapBufferAllocator;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.CompactList;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

public class JobSubscriptionManager extends Actor implements TransportListener
{
    protected static final String NAME = "jobqueue.subscription.manager";
    public static final int NUM_CONCURRENT_REQUESTS = 1_024;

    protected final StreamProcessorServiceFactory streamProcessorServiceFactory;
    protected final ServiceContainer serviceContext;
    private final ServerTransport transport;

    protected final Int2ObjectHashMap<PartitionBucket> logStreamBuckets = new Int2ObjectHashMap<>();
    protected final Long2ObjectHashMap<LockJobStreamProcessor> streamProcessorBySubscriptionId = new Long2ObjectHashMap<>();

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

    public JobSubscriptionManager(ServiceContainer serviceContainer, StreamProcessorServiceFactory streamProcessorServiceFactory, ServerTransport transport)
    {
        this.transport = transport;
        this.serviceContext = serviceContainer;
        this.streamProcessorServiceFactory = streamProcessorServiceFactory;

        this.creditRequestBuffer = new CreditsRequestBuffer(
            NUM_CONCURRENT_REQUESTS,
            (r) ->
            {
                final boolean dispatched = dispatchSubscriptionCredits(r);
                if (!dispatched)
                {
                    backpressureRequest(r);
                }
            });
        this.backPressuredCreditsRequests = new CompactList(CreditsRequest.LENGTH, creditRequestBuffer.getCapacityUpperBound(), new HeapBufferAllocator());
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    public ActorFuture<Void> addSubscription(final JobSubscription subscription)
    {
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
        actor.call(() ->
        {
            final DirectBuffer jobType = subscription.getLockJobType();
            final int partitionId = subscription.getPartitionId();

            final PartitionBucket partitionBucket = logStreamBuckets.get(partitionId);
            if (partitionBucket == null)
            {
                future.completeExceptionally(new RuntimeException(String.format("Partition with id '%d' not found.", partitionId)));
                return;
            }

            final long subscriptionId = nextSubscriptionId++;
            subscription.setSubscriberKey(subscriptionId);

            final LockJobStreamProcessor streamProcessor = partitionBucket.getStreamProcessorByJobType(jobType);
            if (streamProcessor != null)
            {
                streamProcessorBySubscriptionId.put(subscriptionId, streamProcessor);

                final ActorFuture<Void> addFuture = streamProcessor.addSubscription(subscription);
                actor.runOnCompletion(addFuture, (aVoid, throwable) ->
                {
                    if (throwable == null)
                    {
                        actor.submit(this::handleCreditRequests);

                        future.complete(null);
                    }
                    else
                    {
                        future.completeExceptionally(throwable);
                    }
                });
            }
            else
            {
                final LockJobStreamProcessor processor = new LockJobStreamProcessor(jobType);

                final ActorFuture<StreamProcessorService> processorFuture = createStreamProcessorService(processor, partitionBucket, jobType);

                actor.runOnCompletion(processorFuture, (service, t) ->
                {
                    if (t == null)
                    {
                        streamProcessorBySubscriptionId.put(subscriptionId, processor);

                        partitionBucket.addStreamProcessor(processor);
                        final ActorFuture<Void> addFuture = processor.addSubscription(subscription);
                        actor.runOnCompletion(addFuture, ((aVoid, throwable) ->
                        {
                            if (throwable == null)
                            {
                                actor.submit(this::handleCreditRequests);

                                future.complete(null);
                            }
                            else
                            {
                                future.completeExceptionally(throwable);
                            }
                        }));
                    }
                    else
                    {
                        future.completeExceptionally(t);
                    }
                });
            }
        });

        return future;
    }

    protected ActorFuture<StreamProcessorService> createStreamProcessorService(
            final LockJobStreamProcessor factory,
            final PartitionBucket partitionBucket,
            final DirectBuffer jobType)
    {
        final TypedStreamEnvironment env = new TypedStreamEnvironment(partitionBucket.getLogStream(), transport.getOutput());

        return streamProcessorServiceFactory.createService(partitionBucket.getPartition(), partitionBucket.getPartitionServiceName())
            .processor(factory.createStreamProcessor(env))
            .processorId(JOB_LOCK_STREAM_PROCESSOR_ID)
            .processorName(streamProcessorName(jobType))
            .build();
    }

    public ActorFuture<Void> removeSubscription(long subscriptionId)
    {
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
        actor.call(() ->
        {
            final LockJobStreamProcessor streamProcessor = streamProcessorBySubscriptionId.remove(subscriptionId);
            if (streamProcessor != null)
            {
                final ActorFuture<Boolean> removeFuture = streamProcessor.removeSubscription(subscriptionId);
                actor.runOnCompletion(removeFuture, (hasSubscriptions, throwable) ->
                {
                    if (throwable == null)
                    {
                        if (!hasSubscriptions)
                        {
                            final ActorFuture<Void> removeProcessorFuture = removeStreamProcessorService(streamProcessor);
                            actor.runOnCompletion(removeProcessorFuture, (b, t) ->
                            {
                                if (t == null)
                                {
                                    future.complete(null);
                                }
                                else
                                {
                                    future.completeExceptionally(t);
                                }
                            });
                        }
                        else
                        {
                            future.complete(null);
                        }
                    }
                    else
                    {
                        future.completeExceptionally(throwable);
                    }
                });
            }
            else
            {
                future.complete(null);
            }
        });
        return future;
    }

    protected ActorFuture<Void> removeStreamProcessorService(final LockJobStreamProcessor streamProcessor)
    {
        final PartitionBucket partitionBucket = logStreamBuckets.get(streamProcessor.getLogStreamPartitionId());

        partitionBucket.removeStreamProcessor(streamProcessor);

        final String logName = partitionBucket.getLogStreamName();
        final DirectBuffer jobType = streamProcessor.getSubscriptedJobType();

        return serviceContext.removeService(LogStreamServiceNames.streamProcessorService(logName, streamProcessorName(jobType)));
    }

    public boolean increaseSubscriptionCreditsAsync(CreditsRequest request)
    {
        final boolean success = creditRequestBuffer.offerRequest(request);

        if (success)
        {
            actor.call(this::handleCreditRequests);
        }

        return success;
    }

    /**
     * @param request
     * @return if request was handled
     */
    protected boolean dispatchSubscriptionCredits(CreditsRequest request)
    {
        final LockJobStreamProcessor streamProcessor = streamProcessorBySubscriptionId.get(request.getSubscriberKey());

        if (streamProcessor != null)
        {
            return streamProcessor.increaseSubscriptionCreditsAsync(request);
        }
        else
        {
            // ignore
            return true;
        }
    }

    protected void backpressureRequest(CreditsRequest request)
    {
        request.appendTo(backPressuredCreditsRequests);
    }

    private void handleCreditRequests()
    {
        dispatchBackpressuredSubscriptionCredits();
        if (backPressuredCreditsRequests.size() == 0)
        {
            // only accept new requests when backpressured ones have been processed
            // this is required to guarantee that backPressuredCreditsRequests won't overflow
            creditRequestBuffer.handleRequests();
        }
    }

    protected void dispatchBackpressuredSubscriptionCredits()
    {
        int nextRequestToConsume = backPressuredCreditsRequests.size() - 1;

        while (nextRequestToConsume >= 0)
        {
            creditsRequest.wrapListElement(backPressuredCreditsRequests, nextRequestToConsume);
            final boolean success = dispatchSubscriptionCredits(creditsRequest);

            if (success)
            {
                backPressuredCreditsRequests.remove(nextRequestToConsume);
                nextRequestToConsume--;
            }
            else
            {
                break;
            }
        }
    }

    public void addPartition(ServiceName<Partition> partitionServiceName, Partition leaderPartition)
    {
        actor.call(() ->
        {
            logStreamBuckets.put(leaderPartition.getInfo().getPartitionId(), new PartitionBucket(leaderPartition, partitionServiceName));
        });
    }

    public void removePartition(Partition leaderPartition)
    {
        actor.call(() ->
        {
            final int partitionId = leaderPartition.getInfo().getPartitionId();
            logStreamBuckets.remove(partitionId);
            removeSubscriptionsForLogStream(partitionId);
        });
    }

    protected void removeSubscriptionsForLogStream(final int partitionId)
    {
        final Set<Entry<Long, LockJobStreamProcessor>> entrySet = streamProcessorBySubscriptionId.entrySet();
        for (Entry<Long, LockJobStreamProcessor> entry : entrySet)
        {
            final LockJobStreamProcessor streamProcessor = entry.getValue();
            if (partitionId == streamProcessor.getLogStreamPartitionId())
            {
                entrySet.remove(entry);
            }
        }
    }

    public void onClientChannelCloseAsync(int channelId)
    {
        actor.call(() ->
        {
            final Iterator<LockJobStreamProcessor> processorIt = streamProcessorBySubscriptionId.values().iterator();
            while (processorIt.hasNext())
            {
                final LockJobStreamProcessor processor = processorIt.next();
                final ActorFuture<Boolean> closeFuture = processor.onClientChannelCloseAsync(channelId);

                actor.runOnCompletion(closeFuture, (hasSubscriptions, throwable) ->
                {
                    if (throwable == null)
                    {

                        if (!hasSubscriptions)
                        {
                            removeStreamProcessorService(processor);
                        }
                    }
                    else
                    {
                        Loggers.SYSTEM_LOGGER.debug("Problem on closing job locking stream processor.", throwable);
                    }
                });
            }
        });
    }

    private static String streamProcessorName(final DirectBuffer jobType)
    {
        return String.format("job-lock.%s", bufferAsString(jobType));
    }


    static class PartitionBucket
    {
        protected final Partition partition;
        protected final ServiceName<Partition> partitionServiceName;

        protected List<LockJobStreamProcessor> streamProcessors = new ArrayList<>();

        PartitionBucket(Partition partition, ServiceName<Partition> partitionServiceName)
        {
            this.partition = partition;
            this.partitionServiceName = partitionServiceName;
        }

        public LogStream getLogStream()
        {
            return partition.getLogStream();
        }

        public Partition getPartition()
        {
            return partition;
        }

        public String getLogStreamName()
        {
            return partition.getLogStream().getLogName();
        }

        public ServiceName<Partition> getPartitionServiceName()
        {
            return partitionServiceName;
        }

        public LockJobStreamProcessor getStreamProcessorByJobType(DirectBuffer jobType)
        {
            LockJobStreamProcessor streamProcessorForType = null;

            final int size = streamProcessors.size();
            int current = 0;

            while (current < size && streamProcessorForType == null)
            {
                final LockJobStreamProcessor streamProcessor = streamProcessors.get(current);

                if (BufferUtil.equals(jobType, streamProcessor.getSubscriptedJobType()))
                {
                    streamProcessorForType = streamProcessor;
                }

                current += 1;
            }

            return streamProcessorForType;
        }

        public void addStreamProcessor(LockJobStreamProcessor streamProcessor)
        {
            streamProcessors.add(streamProcessor);
        }

        public void removeStreamProcessor(LockJobStreamProcessor streamProcessor)
        {
            streamProcessors.remove(streamProcessor);
        }
    }

    @Override
    public void onConnectionEstablished(RemoteAddress remoteAddress)
    {
    }

    @Override
    public void onConnectionClosed(RemoteAddress remoteAddress)
    {
        onClientChannelCloseAsync(remoteAddress.getStreamId());
    }
}
