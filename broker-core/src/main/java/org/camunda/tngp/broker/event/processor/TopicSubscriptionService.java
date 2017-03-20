package org.camunda.tngp.broker.event.processor;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.AGENT_RUNNER_SERVICE;

import java.io.File;
import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.camunda.tngp.broker.event.TopicSubscriptionNames;
import org.camunda.tngp.broker.logstreams.processor.MetadataFilter;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorIds;
import org.camunda.tngp.broker.logstreams.processor.StreamProcessorService;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
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
    protected Int2ObjectHashMap<TopicSubscriptionManagementProcessor> managersByLog = new Int2ObjectHashMap<>();

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
                logStream.getId(),
                logStreamServiceName,
                indexStore,
                new CommandResponseWriter(sendBufferInjector.getValue()),
                () -> new SubscribedEventWriter(new SingleMessageWriter(sendBufferInjector.getValue())),
                serviceContext
                );

            createStreamProcessorService(
                    logStreamServiceName,
                    TopicSubscriptionNames.subscriptionManagementServiceName(logStream.getLogName()),
                    StreamProcessorIds.TOPIC_SUBSCRIPTION_ACK_PROCESSOR_ID,
                    ackProcessor,
                    TopicSubscriptionManagementProcessor.filter())
                .thenAccept((v) -> managersByLog.put(logStream.getId(), ackProcessor));
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
            final String indexFile = config.snapshotDirectory + File.separator + "ack-index." + logName;
            final FileChannel indexFileChannel = FileUtil.openChannel(indexFile, true);
            return new FileChannelIndexStore(indexFileChannel);
        }
        else
        {
            throw new RuntimeException(String.format("Cannot create topic subscription processor index, no index file name provided."));
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

    protected CompletableFuture<Void> removeStreamProcessorService(ServiceName<StreamProcessorController> processorName)
    {
        return serviceContext.removeService(processorName);
    }

    public void onStreamRemoved(ServiceName<LogStream> logStreamServiceName, LogStream logStream)
    {

        asyncContext.runAsync(() ->
        {
            managersByLog.remove(logStream.getId());

            final ServiceName<StreamProcessorController> ackServiceName =
                    TopicSubscriptionNames.subscriptionManagementServiceName(logStream.getLogName());
            removeStreamProcessorService(ackServiceName);
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

    public CompletableFuture<Void> createSubscriptionAsync(TopicSubscription subscription)
    {
        final TopicSubscriptionManagementProcessor managementProcessor = managersByLog.get(subscription.getTopicId());

        if (managementProcessor != null)
        {
            return managementProcessor.openSubscriptionAsync(subscription);
        }
        else
        {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("No subscription management processor registered for topic " + subscription.getTopicId()));
            return future;
        }

    }

    public CompletableFuture<Void> closeSubscriptionAsync(int topicId, long subscriptionId)
    {
        final TopicSubscriptionManagementProcessor managementProcessor = managersByLog.get(topicId);

        if (managementProcessor != null)
        {
            return managementProcessor.closeSubscriptionAsync(subscriptionId);
        }
        else
        {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("No subscription management processor registered for topic " + topicId));
            return future;
        }

    }
}
