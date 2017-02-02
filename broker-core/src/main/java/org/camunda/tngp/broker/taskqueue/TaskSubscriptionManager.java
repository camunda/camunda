/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.taskqueue;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.logStreamServiceName;
import static org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds.TASK_LOCK_STREAM_PROCESSOR_ID;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.taskQueueLockStreamProcessorServiceName;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.taskqueue.processor.LockTaskStreamProcessor;
import org.camunda.tngp.broker.taskqueue.processor.TaskSubscription;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;

public class TaskSubscriptionManager implements Agent
{
    protected static final String NAME = "taskqueue.subscription.manager";

    protected final ServiceStartContext serviceContext;
    protected final IdGenerator subscriptionIdGenerator;
    protected final Supplier<LockTaskStreamProcessor> streamProcessorSupplier;

    protected final Long2ObjectHashMap<LogStream> logStreamsById = new Long2ObjectHashMap<>();
    protected final Long2ObjectHashMap<LockTaskStreamProcessor> processorByStreamId = new Long2ObjectHashMap<>();

    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected final Consumer<Runnable> cmdConsumer = (c) -> c.run();

    public TaskSubscriptionManager(ServiceStartContext serviceContext)
    {
        this(serviceContext, new PrivateIdGenerator(0L), () -> new LockTaskStreamProcessor());
    }

    public TaskSubscriptionManager(
            ServiceStartContext serviceContext,
            IdGenerator subscriptionIdGenerator,
            Supplier<LockTaskStreamProcessor> streamProcessorSupplier)
    {
        this.serviceContext = serviceContext;
        this.subscriptionIdGenerator = subscriptionIdGenerator;
        this.streamProcessorSupplier = streamProcessorSupplier;
    }

    @Override
    public String roleName()
    {
        return NAME;
    }

    @Override
    public int doWork() throws Exception
    {
        return cmdQueue.drain(cmdConsumer);
    }

    public CompletableFuture<Long> addSubscription(TaskSubscription subscription)
    {
        final CompletableFuture<Long> future = new CompletableFuture<>();

        cmdQueue.add(() ->
        {
            final long streamId = subscription.getTopicId();

            final long subscriptionId = subscriptionIdGenerator.nextId();
            subscription.setId(subscriptionId);

            final LockTaskStreamProcessor streamProcessor = processorByStreamId.get(streamId);
            if (streamProcessor != null)
            {
                streamProcessor
                    .addSubscription(subscription)
                    .handle((r, t) -> t == null ? future.complete(subscriptionId) : future.completeExceptionally(t));
            }
            else
            {
                createStreamProcessorService(streamId)
                    .thenCompose(processor ->
                    {
                        processorByStreamId.put(streamId, processor);

                        return processor.addSubscription(subscription);
                    })
                    .handle((r, t) -> t == null ? future.complete(subscriptionId) : future.completeExceptionally(t));
            }
        });
        return future;
    }

    protected CompletableFuture<LockTaskStreamProcessor> createStreamProcessorService(long logStreamId)
    {
        final CompletableFuture<LockTaskStreamProcessor> future = new CompletableFuture<>();

        final LogStream logStream = logStreamsById.get(logStreamId);
        if (logStream != null)
        {
            final String logName = logStream.getContext().getLogName();

            final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logName);

            final ServiceName<StreamProcessorController> streamProcessorServiceName = taskQueueLockStreamProcessorServiceName(logName);
            final String streamProcessorName = streamProcessorServiceName.getName();

            final LockTaskStreamProcessor streamProcessor = streamProcessorSupplier.get();
            final StreamProcessorService streamProcessorService = new StreamProcessorService(streamProcessorName, TASK_LOCK_STREAM_PROCESSOR_ID, streamProcessor);

            serviceContext.createService(streamProcessorServiceName, streamProcessorService)
                    .dependency(logStreamServiceName, streamProcessorService.getSourceStreamInjector())
                    .dependency(logStreamServiceName, streamProcessorService.getTargetStreamInjector())
                    .dependency(SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
                    .dependency(AGENT_RUNNER_SERVICE, streamProcessorService.getAgentRunnerInjector())
                    .install()
                    .thenApply(Void -> future.complete(streamProcessor));
        }
        else
        {
            final String errorMessage = String.format("Topic with id '%s' not found.", logStreamId);
            future.completeExceptionally(new RuntimeException(errorMessage));
        }
        return future;
    }

    public CompletableFuture<Void> removeSubscription(TaskSubscription subscription)
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        cmdQueue.add(() ->
        {
            final LockTaskStreamProcessor streamProcessor = processorByStreamId.get(subscription.getTopicId());
            if (streamProcessor != null)
            {
                streamProcessor
                    .removeSubscription(subscription.getId())
                    .thenCompose(hasSubscriptions -> !hasSubscriptions ? removeStreamProcessorService(subscription) : CompletableFuture.completedFuture(null))
                    .handle((r, t) -> t == null ? future.complete(null) : future.completeExceptionally(t));
            }
            else
            {
                future.complete(null);
            }
        });
        return future;
    }

    protected CompletionStage<Void> removeStreamProcessorService(TaskSubscription subscription)
    {
        final LogStream logStream = logStreamsById.get(subscription.getTopicId());
        final String logName = logStream.getContext().getLogName();
        final ServiceName<StreamProcessorController> streamProcessorServiceName = taskQueueLockStreamProcessorServiceName(logName);

        return serviceContext.removeService(streamProcessorServiceName);
    }

    public CompletableFuture<Void> updateSubscriptionCredits(TaskSubscription subscription)
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        cmdQueue.add(() ->
        {
            final LockTaskStreamProcessor streamProcessor = processorByStreamId.get(subscription.getTopicId());
            if (streamProcessor != null)
            {
                streamProcessor
                    .updateSubscriptionCredits(subscription.getId(), subscription.getCredits())
                    .handle((r, t) -> t == null ? future.complete(null) : future.completeExceptionally(t));
            }
            else
            {
                final String errorMessage = String.format("Subscription with id '%s' not found.", subscription.getId());
                future.completeExceptionally(new RuntimeException(errorMessage));
            }
        });
        return future;
    }

    public void addStream(LogStream logStream)
    {
        cmdQueue.add(() -> logStreamsById.put(logStream.getId(), logStream));
    }

    public void removeStream(LogStream logStream)
    {
        cmdQueue.add(() ->
        {
            final int logStreamId = logStream.getId();

            logStreamsById.remove(logStreamId);
            processorByStreamId.remove(logStreamId);
        });
    }

}
