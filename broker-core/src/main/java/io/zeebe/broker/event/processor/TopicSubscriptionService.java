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
package io.zeebe.broker.event.processor;

import static io.zeebe.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.agrona.collections.Int2ObjectHashMap;

import io.zeebe.broker.event.TopicSubscriptionServiceNames;
import io.zeebe.broker.logstreams.processor.MetadataFilter;
import io.zeebe.broker.logstreams.processor.StreamProcessorIds;
import io.zeebe.broker.logstreams.processor.StreamProcessorService;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.transport.clientapi.CommandResponseWriter;
import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.broker.transport.clientapi.SubscribedEventWriter;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport. ServerTransport;
import io.zeebe.transport.TransportListener;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

public class TopicSubscriptionService implements Service<TopicSubscriptionService>, Actor, TransportListener
{
    protected final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    protected final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    protected final SubscriptionCfg config;

    protected ActorScheduler actorScheduler;
    protected ServiceStartContext serviceContext;
    protected Int2ObjectHashMap<TopicSubscriptionManagementProcessor> managersByLog = new Int2ObjectHashMap<>();
    protected ServerOutput serverOutput;

    protected ActorReference actorRef;

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

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public Injector< ServerTransport> getClientApiTransportInjector()
    {
        return clientApiTransportInjector;
    }

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ServerTransport transport = clientApiTransportInjector.getValue();
        this.serverOutput = transport.getOutput();

        actorScheduler = actorSchedulerInjector.getValue();
        asyncContext = new DeferredCommandContext();
        this.serviceContext = startContext;

        final CompletableFuture<Void> registration = transport.registerChannelListener(this);
        startContext.async(registration);

        actorRef = actorScheduler.schedule(this);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        actorRef.close();
    }

    public void onStreamAdded(ServiceName<LogStream> logStreamServiceName, LogStream logStream)
    {
        asyncContext.runAsync(() ->
        {
            final TopicSubscriptionManagementProcessor ackProcessor = new TopicSubscriptionManagementProcessor(
                logStreamServiceName,
                new CommandResponseWriter(serverOutput),
                new ErrorResponseWriter(serverOutput),
                () -> new SubscribedEventWriter(serverOutput),
                serviceContext
                );

            createStreamProcessorService(
                    logStreamServiceName,
                    TopicSubscriptionServiceNames.subscriptionManagementServiceName(logStream.getLogName()),
                    StreamProcessorIds.TOPIC_SUBSCRIPTION_MANAGEMENT_PROCESSOR_ID,
                    ackProcessor,
                    TopicSubscriptionManagementProcessor.filter())
                .thenAccept((v) ->
                    managersByLog.put(logStream.getPartitionId(), ackProcessor)
                );
        });
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
            .dependency(ACTOR_SCHEDULER_SERVICE, streamProcessorService.getActorSchedulerInjector())
            .install();
    }

    public void onStreamRemoved(ServiceName<LogStream> logStreamServiceName, LogStream logStream)
    {

        asyncContext.runAsync(() -> managersByLog.remove(logStream.getPartitionId()));
    }

    public void onClientChannelCloseAsync(int channelId)
    {
        asyncContext.runAsync(() ->
        {
            // TODO(menski): probably not garbage free
            managersByLog.forEach((partitionId, manager) -> manager.onClientChannelCloseAsync(channelId));
        });
    }

    @Override
    public int doWork() throws Exception
    {
        return asyncContext.doWork();
    }

    @Override
    public int getPriority(long now)
    {
        return PRIORITY_LOW;
    }

    @Override
    public String name()
    {
        return "subscription-service";
    }

    public CompletableFuture<Void> closeSubscriptionAsync(final int partitionId, final long subscriberKey)
    {
        final TopicSubscriptionManagementProcessor managementProcessor = getManager(partitionId);

        if (managementProcessor != null)
        {
            return managementProcessor.closePushProcessorAsync(subscriberKey);
        }
        else
        {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(
                new RuntimeException(
                    String.format("No subscription management processor registered for partition '%d'", partitionId)
                )
            );
            return future;
        }

    }

    private TopicSubscriptionManagementProcessor getManager(final int partitionId)
    {
        return managersByLog.get(partitionId);
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
