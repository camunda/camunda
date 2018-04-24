/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.commands.CreateTopicCommandStep1;
import io.zeebe.client.api.commands.TopicsRequestStep1;
import io.zeebe.client.api.commands.TopologyRequestStep1;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.cmd.Request;
import io.zeebe.client.impl.clustering.ClientTopologyManager;
import io.zeebe.client.impl.clustering.TopologyRequestImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.impl.subscription.SubscriptionManager;
import io.zeebe.client.impl.topic.CreateTopicCommandImpl;
import io.zeebe.client.impl.topic.TopicsRequestImpl;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ClientTransportBuilder;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.memory.BlockingMemoryPool;
import io.zeebe.transport.impl.memory.UnboundedMemoryPool;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ActorClock;

public class ZeebeClientImpl implements ZeebeClient
{
    public static final Logger LOG = Loggers.CLIENT_LOGGER;

    public static final String VERSION;

    static
    {
        final String version = ZeebeClient.class.getPackage().getImplementationVersion();
        VERSION = version != null ? version : "development";
    }

    protected final ZeebeClientConfiguration configuration;

    protected Dispatcher dataFrameReceiveBuffer;
    protected ActorScheduler scheduler;

    protected ClientTransport transport;

    protected final ZeebeObjectMapperImpl objectMapper;
    protected final MsgPackConverter msgPackConverter;

    protected final ClientTopologyManager topologyManager;
    protected final RequestManager apiCommandManager;
    protected SubscriptionManager subscriptionManager;

    protected final int subscriptionPrefetchCapacity;

    protected boolean isClosed;

    public ZeebeClientImpl(final ZeebeClientConfiguration configuration)
    {
        this(configuration, null);
    }

    public ZeebeClientImpl(final ZeebeClientConfiguration configuration, ActorClock actorClock)
    {
        LOG.info("Version: {}", VERSION);

        this.configuration = configuration;

        final SocketAddress contactPoint = SocketAddress.from(configuration.getBrokerContactPoint());

        this.scheduler = ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(configuration.getNumManagementThreads())
            .setIoBoundActorThreadCount(0)
            .setActorClock(actorClock)
            .setSchedulerName("client")
            .build();
        this.scheduler.start();

        final ByteValue sendBufferSize = ByteValue.ofMegabytes(configuration.getSendBufferSize());
        final long requestBlockTimeMs = configuration.getRequestBlocktime().toMillis();

        dataFrameReceiveBuffer = Dispatchers.create("receive-buffer")
            .bufferSize(sendBufferSize)
            .modePubSub()
            .frameMaxLength(1024 * 1024)
            .actorScheduler(scheduler)
            .build();

        final ClientTransportBuilder transportBuilder = Transports.newClientTransport()
            .messageMaxLength(1024 * 1024)
            .messageReceiveBuffer(dataFrameReceiveBuffer)
            .messageMemoryPool(new UnboundedMemoryPool()) // Client is not sending any heavy messages
            .requestMemoryPool(new BlockingMemoryPool(sendBufferSize, requestBlockTimeMs))
            .scheduler(scheduler);

        if (configuration.getTcpChannelKeepAlivePeriod() != null)
        {
            transportBuilder.keepAlivePeriod(configuration.getTcpChannelKeepAlivePeriod());
        }

        transport = transportBuilder.build();

        this.msgPackConverter = new MsgPackConverter();
        this.objectMapper = new ZeebeObjectMapperImpl(msgPackConverter);

        subscriptionPrefetchCapacity = configuration.getTopicSubscriptionPrefetchCapacity();

        final RemoteAddress initialContactPoint = transport.registerRemoteAddress(contactPoint);

        topologyManager = new ClientTopologyManager(transport, objectMapper, initialContactPoint);
        scheduler.submitActor(topologyManager);

        apiCommandManager = new RequestManager(
            transport.getOutput(),
            topologyManager,
            objectMapper,
            configuration.getRequestTimeout(),
            requestBlockTimeMs);
        this.scheduler.submitActor(apiCommandManager);

        this.subscriptionManager = new SubscriptionManager(this);
        this.transport.registerChannelListener(subscriptionManager);
        this.scheduler.submitActor(subscriptionManager);
    }

    @Override
    public void close()
    {
        if (isClosed)
        {
            return;
        }

        isClosed = true;

        LOG.debug("Closing client ...");

        doAndLogException(() -> subscriptionManager.close().join());
        LOG.debug("subscriber group manager closed");
        doAndLogException(() -> apiCommandManager.close().join());
        LOG.debug("api command manager closed");
        doAndLogException(() -> topologyManager.close().join());
        LOG.debug("topology manager closed");
        doAndLogException(() -> transport.close());
        LOG.debug("data frame receive buffer closed");
        doAndLogException(() -> dataFrameReceiveBuffer.close());

        try
        {
            scheduler.stop().get(15, TimeUnit.SECONDS);

            LOG.debug("Client closed.");
        }
        catch (InterruptedException | ExecutionException | TimeoutException e)
        {
            throw new RuntimeException("Could not shutdown client successfully", e);
        }
    }

    protected void doAndLogException(Runnable r)
    {
        try
        {
            r.run();
        }
        catch (Exception e)
        {
            Loggers.CLIENT_LOGGER.error("Exception when closing client. Ignoring", e);
        }
    }

    @Override
    public Request<Workflow> requestWorkflowDefinitionByKey(long key)
    {
        return new RequestWorkflowDefinitionByKey(apiCommandManager, topologyManager, key);
    }

    public RequestManager getCommandManager()
    {
        return apiCommandManager;
    }

    public ClientTopologyManager getTopologyManager()
    {
        return topologyManager;
    }

    public ZeebeObjectMapperImpl getObjectMapper()
    {
        return objectMapper;
    }

    @Override
    public ZeebeClientConfiguration getConfiguration()
    {
        return configuration;
    }

    public ClientTransport getTransport()
    {
        return transport;
    }

    public MsgPackConverter getMsgPackConverter()
    {
        return msgPackConverter;
    }

    public ActorScheduler getScheduler()
    {
        return scheduler;
    }

    public SubscriptionManager getSubscriptionManager()
    {
        return subscriptionManager;
    }

    public int getSubscriptionPrefetchCapacity()
    {
        return subscriptionPrefetchCapacity;
    }

    @Override
    public TopicClient topicClient(String topicName)
    {
        return new TopicClientImpl(this, topicName);
    }

    @Override
    public TopicClient topicClient()
    {
        // TODO resolve the default topic
        return new TopicClientImpl(this, "default-topic");
    }

    @Override
    public ZeebeObjectMapper objectMapper()
    {
        return objectMapper;
    }

    @Override
    public CreateTopicCommandStep1 newCreateTopicCommand()
    {
        return new CreateTopicCommandImpl(getCommandManager());
    }

    @Override
    public TopicsRequestStep1 newTopicsRequest()
    {
        return new TopicsRequestImpl(getCommandManager());
    }

    @Override
    public TopologyRequestStep1 newTopologyRequest()
    {
        return new TopologyRequestImpl(getCommandManager(), topologyManager);
    }
}
