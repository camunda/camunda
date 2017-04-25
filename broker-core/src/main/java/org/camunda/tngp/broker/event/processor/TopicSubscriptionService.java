package org.camunda.tngp.broker.event.processor;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;
import static org.camunda.tngp.util.buffer.BufferUtil.bufferAsString;

import java.io.File;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.camunda.tngp.broker.event.TopicSubscriptionServiceNames;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.transport.clientapi.ErrorResponseWriter;
import org.camunda.tngp.broker.transport.clientapi.SingleMessageWriter;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.FileUtil;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class TopicSubscriptionService implements Service<TopicSubscriptionService>, Agent
{
    protected final Injector<AgentRunnerServices> agentRunnerServicesInjector = new Injector<>();
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    protected final SubscriptionCfg config;

    protected AgentRunnerService agentRunnerService;
    protected ServiceStartContext serviceContext;
    protected Map<DirectBuffer, Int2ObjectHashMap<TopicSubscriptionManagementProcessor>> managersByLog = new HashMap<>();

    protected DeferredCommandContext asyncContext;

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
        .onAdd(this::onStreamAdded)
        .onRemove(this::onStreamRemoved)
        .build();


    public TopicSubscriptionService(ConfigurationManager configurationManager)
    {
        config = configurationManager.readEntry("subscriptions", SubscriptionCfg.class);
        Objects.requireNonNull(config);
    }

    @Override
    public TopicSubscriptionService get()
    {
        return this;
    }

    public Injector<AgentRunnerServices> getAgentRunnerServicesInjector()
    {
        return agentRunnerServicesInjector;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        agentRunnerService = agentRunnerServicesInjector.getValue().conductorAgentRunnerService();
        asyncContext = new DeferredCommandContext();
        this.serviceContext = startContext;

        agentRunnerService.run(this);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        agentRunnerService.remove(this);
    }

    public void onStreamAdded(ServiceName<LogStream> logStreamServiceName, LogStream logStream)
    {
        asyncContext.runAsync(() ->
        {
            final IndexStore indexStore = newIndexStoreForLog(logStream.getLogName());

            final TopicSubscriptionManagementProcessor ackProcessor = new TopicSubscriptionManagementProcessor(
                logStreamServiceName,
                indexStore,
                new CommandResponseWriter(sendBufferInjector.getValue()),
                new ErrorResponseWriter(sendBufferInjector.getValue()),
                () -> new SubscribedEventWriter(new SingleMessageWriter(sendBufferInjector.getValue())),
                serviceContext
                );

            createStreamProcessorService(
                    logStreamServiceName,
                    TopicSubscriptionServiceNames.subscriptionManagementServiceName(logStream.getLogName()),
                    StreamProcessorIds.TOPIC_SUBSCRIPTION_MANAGEMENT_PROCESSOR_ID,
                    ackProcessor,
                    TopicSubscriptionManagementProcessor.filter())
                .thenAccept((v) ->
                    managersByLog
                        .computeIfAbsent(logStream.getTopicName(), k -> new Int2ObjectHashMap<>())
                        .put(logStream.getPartitionId(), ackProcessor)
                );
        });
    }

    protected IndexStore newIndexStoreForLog(String logName)
    {
        if (config.useTempSnapshotFile)
        {
            return FileChannelIndexStore.tempFileIndexStore();
        }
        else if (config.snapshotDirectory != null && !config.snapshotDirectory.isEmpty())
        {
            final String indexFile = config.snapshotDirectory + File.separator + "ack-index." + logName + ".idx";
            final FileChannel indexFileChannel = FileUtil.openChannel(indexFile, true);
            return new FileChannelIndexStore(indexFileChannel);
        }
        else
        {
            throw new RuntimeException("Cannot create topic subscription processor index, no index file name provided.");
        }
    }


    protected CompletableFuture<Void> createStreamProcessorService(
            ServiceName<LogStream> logStreamName,
            ServiceName<StreamProcessorController> processorName,
            int processorId,
            StreamProcessor streamProcessor,
            MetadataFilter eventFilter)
    {
        final StreamProcessorService streamProcessorService = new StreamProcessorService(
                processorName.getName(),
                processorId,
                streamProcessor)
            .eventFilter(eventFilter);

        return serviceContext.createService(processorName, streamProcessorService)
            .dependency(logStreamName, streamProcessorService.getSourceStreamInjector())
            .dependency(logStreamName, streamProcessorService.getTargetStreamInjector())
            .dependency(SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
            .dependency(AGENT_RUNNER_SERVICE, streamProcessorService.getAgentRunnerInjector())
            .install();
    }

    public void onStreamRemoved(ServiceName<LogStream> logStreamServiceName, LogStream logStream)
    {

        asyncContext.runAsync(() ->
        {
            final DirectBuffer topicName = logStream.getTopicName();
            final int partitionId = logStream.getPartitionId();

            final Int2ObjectHashMap<TopicSubscriptionManagementProcessor> managersByPartition = managersByLog.get(topicName);

            if (managersByPartition != null)
            {
                managersByPartition.remove(partitionId);

                if (managersByPartition.isEmpty())
                {
                    managersByLog.remove(topicName);
                }
            }
        });
    }

    public void onClientChannelCloseAsync(int channelId)
    {
        asyncContext.runAsync(() ->
        {
            // TODO(menski): probably not garbage free
            managersByLog.forEach((topicName, partitions) ->
                partitions.forEach((partitionId, manager) ->
                    manager.onClientChannelCloseAsync(channelId)
                )
            );
        });
    }

    @Override
    public int doWork() throws Exception
    {
        return asyncContext.doWork();
    }

    @Override
    public String roleName()
    {
        return "subscription-service";
    }

    public CompletableFuture<Void> closeSubscriptionAsync(final DirectBuffer topicName, final int partitionId, final long subscriberKey)
    {
        final TopicSubscriptionManagementProcessor managementProcessor = getManager(topicName, partitionId);

        if (managementProcessor != null)
        {
            return managementProcessor.closePushProcessorAsync(subscriberKey);
        }
        else
        {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(
                new RuntimeException(
                    String.format("No subscription management processor registered for topic '%s' and partition '%d'",
                        bufferAsString(topicName), partitionId)
                )
            );
            return future;
        }

    }

    private TopicSubscriptionManagementProcessor getManager(final DirectBuffer topicName, final int partitionId)
    {
        final Int2ObjectHashMap<TopicSubscriptionManagementProcessor> managersByPartition = managersByLog.get(topicName);

        if (managersByPartition != null)
        {
            return managersByPartition.get(partitionId);
        }

        return null;
    }
}
