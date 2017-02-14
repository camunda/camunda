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
import static org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds.TASK_LOCK_STREAM_PROCESSOR_ID;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.broker.taskqueue.TaskQueueServiceNames.taskQueueLockStreamProcessorServiceName;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.taskqueue.processor.LockTaskStreamProcessor;
import org.camunda.tngp.broker.taskqueue.processor.TaskSubscription;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.util.buffer.BufferUtil;

public class TaskSubscriptionManager implements Agent
{
    protected static final String NAME = "taskqueue.subscription.manager";

    protected final ServiceStartContext serviceContext;
    protected final Function<DirectBuffer, LockTaskStreamProcessor> streamProcessorSupplier;

    protected final Long2ObjectHashMap<LogStreamBucket> logStreamBucketById = new Long2ObjectHashMap<>();

    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue = new ManyToOneConcurrentArrayQueue<>(100);
    protected final Consumer<Runnable> cmdConsumer = (c) -> c.run();

    protected long nextSubscriptionId = 0;

    public TaskSubscriptionManager(ServiceStartContext serviceContext)
    {
        this(serviceContext, taskType -> new LockTaskStreamProcessor(taskType));
    }

    public TaskSubscriptionManager(
            ServiceStartContext serviceContext,
            Function<DirectBuffer, LockTaskStreamProcessor> streamProcessorBuilder)
    {
        this.serviceContext = serviceContext;
        this.streamProcessorSupplier = streamProcessorBuilder;
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

    protected <T> CompletableFuture<T> runAsync(Consumer<CompletableFuture<T>> action)
    {
        final CompletableFuture<T> future = new CompletableFuture<>();

        cmdQueue.add(() ->
        {
            try
            {
                action.accept(future);
            }
            catch (Exception e)
            {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> addSubscription(TaskSubscription subscription)
    {
        return runAsync(future ->
        {
            ensureNotNull("subscription", subscription);

            final long streamId = subscription.getTopicId();
            final DirectBuffer taskType = subscription.getLockTaskType();

            ensureNotNull("lock task type", taskType);

            final LogStreamBucket logStreamBucket = logStreamBucketById.get(streamId);
            if (logStreamBucket == null)
            {
                final String errorMessage = String.format("Topic with id '%s' not found.", streamId);
                throw new RuntimeException(errorMessage);
            }

            final long subscriptionId = nextSubscriptionId++;
            subscription.setId(subscriptionId);

            final LockTaskStreamProcessor streamProcessor = logStreamBucket.getStreamProcessorByTaskType(taskType);
            if (streamProcessor != null)
            {
                streamProcessor
                    .addSubscription(subscription)
                    .handle((r, t) -> t == null ? future.complete(null) : future.completeExceptionally(t));
            }
            else
            {
                createStreamProcessorService(logStreamBucket, taskType)
                    .thenCompose(processor ->
                    {
                        logStreamBucket.addStreamProcessor(processor);

                        return processor.addSubscription(subscription);
                    })
                    .handle((r, t) -> t == null ? future.complete(null) : future.completeExceptionally(t));
            }
        });
    }

    protected CompletableFuture<LockTaskStreamProcessor> createStreamProcessorService(final LogStreamBucket logStreamBucket, final DirectBuffer taskType)
    {
        final CompletableFuture<LockTaskStreamProcessor> future = new CompletableFuture<>();

        final ServiceName<LogStream> logStreamServiceName = logStreamBucket.getLogServiceName();

        final ServiceName<StreamProcessorController> streamProcessorServiceName = taskQueueLockStreamProcessorServiceName(logStreamBucket.getLogStreamName(), BufferUtil.bufferAsString(taskType));
        final String streamProcessorName = streamProcessorServiceName.getName();

        final LockTaskStreamProcessor streamProcessor = streamProcessorSupplier.apply(taskType);
        final StreamProcessorService streamProcessorService = new StreamProcessorService(
                streamProcessorName,
                TASK_LOCK_STREAM_PROCESSOR_ID,
                streamProcessor)
            .eventFilter(LockTaskStreamProcessor.eventFilter());

        serviceContext.createService(streamProcessorServiceName, streamProcessorService)
            .dependency(logStreamServiceName, streamProcessorService.getSourceStreamInjector())
            .dependency(logStreamServiceName, streamProcessorService.getTargetStreamInjector())
            .dependency(SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
            .dependency(AGENT_RUNNER_SERVICE, streamProcessorService.getAgentRunnerInjector())
            .install()
            .thenApply(Void -> future.complete(streamProcessor));

        return future;
    }

    public CompletableFuture<Void> removeSubscription(TaskSubscription subscription)
    {
        return runAsync(future ->
        {
            boolean isScheduled = false;

            ensureNotNull("subscription", subscription);

            final long logStreamId = subscription.getTopicId();
            final DirectBuffer taskType = subscription.getLockTaskType();

            ensureNotNull("lock task type", taskType);

            final LogStreamBucket logStreamBucket = logStreamBucketById.get(logStreamId);
            if (logStreamBucket != null)
            {
                final LockTaskStreamProcessor streamProcessor = logStreamBucket.getStreamProcessorByTaskType(taskType);
                if (streamProcessor != null)
                {
                    streamProcessor
                        .removeSubscription(subscription.getId())
                        .thenCompose(hasSubscriptions -> !hasSubscriptions ? removeStreamProcessorService(logStreamBucket, taskType) : CompletableFuture.completedFuture(null))
                        .handle((r, t) -> t == null ? future.complete(null) : future.completeExceptionally(t));

                    isScheduled = true;
                }
            }

            if (!isScheduled)
            {
                future.complete(null);
            }
        });
    }

    protected CompletionStage<Void> removeStreamProcessorService(final LogStreamBucket logStreamBucket, final DirectBuffer taskType)
    {
        final String logName = logStreamBucket.getLogStreamName();
        final ServiceName<StreamProcessorController> streamProcessorServiceName = taskQueueLockStreamProcessorServiceName(logName, BufferUtil.bufferAsString(taskType));

        return serviceContext.removeService(streamProcessorServiceName);
    }

    public CompletableFuture<Void> updateSubscriptionCredits(TaskSubscription subscription)
    {
        return runAsync(future ->
        {
            boolean isUpdated = false;

            ensureNotNull("subscription", subscription);

            final long logStreamId = subscription.getTopicId();
            final DirectBuffer taskType = subscription.getLockTaskType();

            ensureNotNull("lock task type", taskType);

            final LogStreamBucket logStreamBucket = logStreamBucketById.get(logStreamId);
            if (logStreamBucket != null)
            {
                final LockTaskStreamProcessor streamProcessor = logStreamBucket.getStreamProcessorByTaskType(taskType);
                if (streamProcessor != null)
                {
                    streamProcessor
                        .updateSubscriptionCredits(subscription.getId(), subscription.getCredits())
                        .handle((r, t) -> t == null ? future.complete(null) : future.completeExceptionally(t));

                    isUpdated = true;
                }
            }

            if (!isUpdated)
            {
                final String errorMessage = String.format("Subscription with id '%s' not found.", subscription.getId());
                future.completeExceptionally(new RuntimeException(errorMessage));
            }
        });
    }

    public void addStream(LogStream logStream, ServiceName<LogStream> logStreamServiceName)
    {
        runAsync(future ->
        {
            final int logStreamId = logStream.getId();
            logStreamBucketById.put(logStreamId, new LogStreamBucket(logStream, logStreamServiceName));
        });
    }

    public void removeStream(LogStream logStream)
    {
        runAsync(future ->
        {
            final int logStreamId = logStream.getId();
            logStreamBucketById.remove(logStreamId);
        });
    }

    class LogStreamBucket
    {
        protected final LogStream logStream;
        protected final String logStreamName;
        protected final ServiceName<LogStream> logStreamServiceName;

        protected List<LockTaskStreamProcessor> streamProcessors = new ArrayList<>();

        LogStreamBucket(LogStream logStream, ServiceName<LogStream> logStreamServiceName)
        {
            this.logStream = logStream;
            this.logStreamServiceName = logStreamServiceName;
            this.logStreamName = logStream.getContext().getLogName();
        }

        public String getLogStreamName()
        {
            return logStreamName;
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

        public LogStream getLogStream()
        {
            return logStream;
        }
    }

}
