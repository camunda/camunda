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

import java.util.Objects;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.event.TopicSubscriptionServiceNames;
import io.zeebe.broker.logstreams.processor.*;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.transport.clientapi.*;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.*;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

public class TopicSubscriptionService extends Actor implements Service<TopicSubscriptionService>, TransportListener
{
    private static final Logger LOG = Loggers.SERVICES_LOGGER;

    protected final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
    protected final SubscriptionCfg config;

    protected ServiceStartContext serviceContext;
    protected Int2ObjectHashMap<TopicSubscriptionManagementProcessor> managersByLog = new Int2ObjectHashMap<>();
    protected ServerOutput serverOutput;


    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
        .onAdd(this::onStreamAdded)
        .onRemove((logStreamServiceName, logStream) -> onStreamRemoved(logStream))
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

        this.serviceContext = startContext;

        final ActorFuture<Void> registration = transport.registerChannelListener(this);
        startContext.async(registration);

        startContext.getScheduler().submitActor(this);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        actor.close();
    }

    public void onStreamAdded(ServiceName<LogStream> logStreamServiceName, LogStream logStream)
    {
        actor.call(() ->
        {
            final TopicSubscriptionManagementProcessor ackProcessor = new TopicSubscriptionManagementProcessor(
                logStreamServiceName,
                new CommandResponseWriter(serverOutput),
                new ErrorResponseWriter(serverOutput),
                () -> new SubscribedEventWriter(serverOutput),
                serviceContext
                );


            final ActorFuture<Void> future = createStreamProcessorService(
                logStreamServiceName,
                TopicSubscriptionServiceNames.subscriptionManagementServiceName(logStream.getLogName()),
                StreamProcessorIds.TOPIC_SUBSCRIPTION_MANAGEMENT_PROCESSOR_ID,
                ackProcessor,
                TopicSubscriptionManagementProcessor.filter());

            actor.runOnCompletion(future, (aVoid, throwable) ->
            {
                if (throwable == null)
                {
                    managersByLog.put(logStream.getPartitionId(), ackProcessor);
                }
                else
                {
                    LOG.error("Failed to create topic subscription stream processor service for log stream service '{}'", logStreamServiceName);
                }
            });
        });
    }

    protected ActorFuture<Void> createStreamProcessorService(
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
                             .dependency(logStreamName, streamProcessorService.getLogStreamInjector())
                             .dependency(SNAPSHOT_STORAGE_SERVICE, streamProcessorService.getSnapshotStorageInjector())
                             .install();
    }

    public void onStreamRemoved(LogStream logStream)
    {
        actor.call(() -> managersByLog.remove(logStream.getPartitionId()));
    }

    public void onClientChannelCloseAsync(int channelId)
    {
        actor.call(() ->
        {
            // TODO(menski): probably not garbage free
            managersByLog.forEach((partitionId, manager) -> manager.onClientChannelCloseAsync(channelId));
        });
    }

    @Override
    public String getName()
    {
        return "subscription-service";
    }

    public ActorFuture<Void> closeSubscriptionAsync(final int partitionId, final long subscriberKey)
    {
        final TopicSubscriptionManagementProcessor managementProcessor = getManager(partitionId);

        if (managementProcessor != null)
        {
            return managementProcessor.closePushProcessorAsync(subscriberKey);
        }
        else
        {
            return CompletableActorFuture.completedExceptionally(new RuntimeException(
                String.format("No subscription management processor registered for partition '%d'", partitionId)
            ));
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
