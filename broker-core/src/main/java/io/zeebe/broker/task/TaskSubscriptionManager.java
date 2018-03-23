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
package io.zeebe.broker.task;

import static io.zeebe.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static io.zeebe.broker.logstreams.processor.StreamProcessorIds.TASK_LOCK_STREAM_PROCESSOR_ID;
import static io.zeebe.broker.task.TaskQueueServiceNames.taskQueueLockStreamProcessorServiceName;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.logstreams.processor.StreamProcessorService;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.task.processor.LockTaskStreamProcessor;
import io.zeebe.broker.task.processor.TaskSubscription;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.TransportListener;
import io.zeebe.util.allocation.HeapBufferAllocator;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.collection.CompactList;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;

public class TaskSubscriptionManager extends Actor implements TransportListener
{
    protected static final String NAME = "taskqueue.subscription.manager";
    public static final int NUM_CONCURRENT_REQUESTS = 1_024;

    protected final ServiceStartContext serviceContext;
    private final ServerTransport transport;

    protected final Int2ObjectHashMap<LogStreamBucket> logStreamBuckets = new Int2ObjectHashMap<>();
    protected final Long2ObjectHashMap<LockTaskStreamProcessor> streamProcessorBySubscriptionId = new Long2ObjectHashMap<>();

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

    public TaskSubscriptionManager(ServiceStartContext serviceContext, ServerTransport transport)
    {
        this.transport = transport;
        this.serviceContext = serviceContext;
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

    public ActorFuture<Void> addSubscription(final TaskSubscription subscription)
    {
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
        actor.call(() ->
        {
            final DirectBuffer taskType = subscription.getLockTaskType();
            final int partitionId = subscription.getPartitionId();

            final LogStreamBucket logStreamBucket = logStreamBuckets.get(partitionId);
            if (logStreamBucket == null)
            {
                future.completeExceptionally(new RuntimeException(String.format("Partition with id '%d' not found.", partitionId)));
                return;
            }

            final long subscriptionId = nextSubscriptionId++;
            subscription.setSubscriberKey(subscriptionId);

            final LockTaskStreamProcessor streamProcessor = logStreamBucket.getStreamProcessorByTaskType(taskType);
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
                final LockTaskStreamProcessor processor = new LockTaskStreamProcessor(taskType);
                final ActorFuture<Void> processorFuture = createStreamProcessorService(processor, taskType, logStreamBucket, taskType);

                actor.runOnCompletion(processorFuture, (v, t) ->
                {
                    if (t == null)
                    {
                        streamProcessorBySubscriptionId.put(subscriptionId, processor);

                        logStreamBucket.addStreamProcessor(processor);
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

    protected ActorFuture<Void> createStreamProcessorService(
            final LockTaskStreamProcessor factory,
            DirectBuffer newTaskTypeBuffer,
            final LogStreamBucket logStreamBucket,
            final DirectBuffer taskType)
    {
        final TypedStreamEnvironment env = new TypedStreamEnvironment(logStreamBucket.getLogStream(), transport.getOutput());

        final TypedStreamProcessor streamProcessor = factory.createStreamProcessor(env);

        final ServiceName<LogStream> logStreamServiceName = logStreamBucket.getLogServiceName();

        final String logName = logStreamBucket.getLogStream().getLogName();
        final ServiceName<StreamProcessorController> streamProcessorServiceName = taskQueueLockStreamProcessorServiceName(logName, bufferAsString(taskType));
        final String streamProcessorName = streamProcessorServiceName.getName();

        final StreamProcessorService streamProcessorService = new StreamProcessorService(
                streamProcessorName,
                TASK_LOCK_STREAM_PROCESSOR_ID,
                streamProcessor)
            .eventFilter(streamProcessor.buildTypeFilter());

        return serviceContext.createService(streamProcessorServiceName, streamProcessorService)
                             .dependency(logStreamServiceName, streamProcessorService.getLogStreamInjector())
                             .dependency(SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
                             .install();
    }

    public ActorFuture<Void> removeSubscription(long subscriptionId)
    {
        final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
        actor.call(() ->
        {
            final LockTaskStreamProcessor streamProcessor = streamProcessorBySubscriptionId.remove(subscriptionId);
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

    protected ActorFuture<Void> removeStreamProcessorService(final LockTaskStreamProcessor streamProcessor)
    {
        final LogStreamBucket logStreamBucket = logStreamBuckets.get(streamProcessor.getLogStreamPartitionId());

        logStreamBucket.removeStreamProcessor(streamProcessor);

        final String logName = logStreamBucket.getLogStream().getLogName();
        final String taskType = bufferAsString(streamProcessor.getSubscriptedTaskType());
        final ServiceName<StreamProcessorController> streamProcessorServiceName = taskQueueLockStreamProcessorServiceName(logName, taskType);

        return serviceContext.removeService(streamProcessorServiceName);
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
        final LockTaskStreamProcessor streamProcessor = streamProcessorBySubscriptionId.get(request.getSubscriberKey());

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

    protected int dispatchBackpressuredSubscriptionCredits()
    {
        int nextRequestToConsume = backPressuredCreditsRequests.size() - 1;
        int numSuccessfulRequests = 0;

        while (nextRequestToConsume >= 0)
        {
            creditsRequest.wrapListElement(backPressuredCreditsRequests, nextRequestToConsume);
            final boolean success = dispatchSubscriptionCredits(creditsRequest);

            if (success)
            {
                backPressuredCreditsRequests.remove(nextRequestToConsume);
                numSuccessfulRequests++;
                nextRequestToConsume--;
            }
            else
            {
                break;
            }
        }

        return numSuccessfulRequests;
    }

    public void addStream(LogStream logStream, ServiceName<LogStream> logStreamServiceName)
    {
        actor.call(() ->
        {
            logStreamBuckets.put(logStream.getPartitionId(), new LogStreamBucket(logStream, logStreamServiceName));
        });
    }

    public void removeStream(LogStream logStream)
    {
        actor.call(() ->
        {
            final int partitionId = logStream.getPartitionId();
            logStreamBuckets.remove(partitionId);
            removeSubscriptionsForLogStream(partitionId);
        });
    }

    protected void removeSubscriptionsForLogStream(final int partitionId)
    {
        final Set<Entry<Long, LockTaskStreamProcessor>> entrySet = streamProcessorBySubscriptionId.entrySet();
        for (Entry<Long, LockTaskStreamProcessor> entry : entrySet)
        {
            final LockTaskStreamProcessor streamProcessor = entry.getValue();
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
            final Iterator<LockTaskStreamProcessor> processorIt = streamProcessorBySubscriptionId.values().iterator();
            while (processorIt.hasNext())
            {
                final LockTaskStreamProcessor processor = processorIt.next();
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
                        Loggers.SYSTEM_LOGGER.debug("Problem on closing LockTaskStreamProcessor.", throwable);
                    }
                });
            }
        });
    }

    public int getCreditRequestCapacityUpperBound()
    {
        return creditRequestBuffer.getCapacityUpperBound();
    }

    static class LogStreamBucket
    {
        protected final LogStream logStream;
        protected final ServiceName<LogStream> logStreamServiceName;

        protected List<LockTaskStreamProcessor> streamProcessors = new ArrayList<>();

        LogStreamBucket(LogStream logStream, ServiceName<LogStream> logStreamServiceName)
        {
            this.logStream = logStream;
            this.logStreamServiceName = logStreamServiceName;
        }

        public LogStream getLogStream()
        {
            return logStream;
        }

        public ServiceName<LogStream> getLogServiceName()
        {
            return logStreamServiceName;
        }

        public LockTaskStreamProcessor getStreamProcessorByTaskType(DirectBuffer taskType)
        {
            LockTaskStreamProcessor streamProcessorForType = null;

            final int size = streamProcessors.size();
            int current = 0;

            while (current < size && streamProcessorForType == null)
            {
                final LockTaskStreamProcessor streamProcessor = streamProcessors.get(current);

                if (BufferUtil.equals(taskType, streamProcessor.getSubscriptedTaskType()))
                {
                    streamProcessorForType = streamProcessor;
                }

                current += 1;
            }

            return streamProcessorForType;
        }

        public void addStreamProcessor(LockTaskStreamProcessor streamProcessor)
        {
            streamProcessors.add(streamProcessor);
        }

        public void removeStreamProcessor(LockTaskStreamProcessor streamProcessor)
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
