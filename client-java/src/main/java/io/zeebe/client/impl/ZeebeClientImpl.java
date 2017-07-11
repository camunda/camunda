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

import static io.zeebe.client.ClientProperties.CLIENT_MAXREQUESTS;
import static io.zeebe.client.ClientProperties.CLIENT_SENDBUFFER_SIZE;
import static io.zeebe.util.EnsureUtil.ensureGreaterThanOrEqual;
import static io.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import java.util.Properties;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.*;
import io.zeebe.client.clustering.RequestTopologyCmd;
import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.clustering.impl.RequestTopologyCmdImpl;
import io.zeebe.client.event.impl.TopicClientImpl;
import io.zeebe.client.task.impl.subscription.SubscriptionManager;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.transport.*;
import io.zeebe.util.actor.*;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class ZeebeClientImpl implements ZeebeClient
{
    protected final Properties initializationProperties;

    protected SocketAddress contactPoint;
    protected Dispatcher dataFrameReceiveBuffer;
    protected Dispatcher sendBuffer;
    protected ActorScheduler transportActorScheduler;

    protected ClientTransport transport;

    protected final ObjectMapper objectMapper;

    protected SubscriptionManager subscriptionManager;

    protected final ClientTopologyManager topologyManager;
    protected final ClientCommandManager commandManager;

    protected ActorReference topologyManagerActorReference;
    protected ActorReference commandManagerActorReference;

    protected boolean connected = false;


    public ZeebeClientImpl(final Properties properties)
    {
        ClientProperties.setDefaults(properties);
        this.initializationProperties = properties;

        contactPoint = SocketAddress.from(properties.getProperty(ClientProperties.BROKER_CONTACTPOINT));

        final int maxRequests = Integer.parseInt(properties.getProperty(CLIENT_MAXREQUESTS));
        final int sendBufferSize = Integer.parseInt(properties.getProperty(CLIENT_SENDBUFFER_SIZE));

        this.transportActorScheduler = ActorSchedulerBuilder.createDefaultScheduler("transport");

        dataFrameReceiveBuffer = Dispatchers.create("receive-buffer")
            .bufferSize(1024 * 1024 * sendBufferSize)
            .modePubSub()
            .frameMaxLength(1024 * 1024)
            .actorScheduler(transportActorScheduler)
            .build();
        sendBuffer = Dispatchers.create("send-buffer")
            .actorScheduler(transportActorScheduler)
            .bufferSize(1024 * 1024 * sendBufferSize)
            .subscriptions("sender")
//                .countersManager(countersManager) // TODO: counters manager
            .build();

        transport = Transports.newClientTransport()
                .messageMaxLength(1024 * 1024)
                .messageReceiveBuffer(dataFrameReceiveBuffer)
                .requestPoolSize(maxRequests)
                .scheduler(transportActorScheduler)
                .sendBuffer(sendBuffer)
                .build();

        // TODO: configure keep-alive
//        final long keepAlivePeriod = Long.parseLong(properties.getProperty(CLIENT_TCP_CHANNEL_KEEP_ALIVE_PERIOD));

        objectMapper = new ObjectMapper(new MessagePackFactory());
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        final int numExecutionThreads = Integer.parseInt(properties.getProperty(ClientProperties.CLIENT_TASK_EXECUTION_THREADS));
        final Boolean autoCompleteTasks = Boolean.parseBoolean(properties.getProperty(ClientProperties.CLIENT_TASK_EXECUTION_AUTOCOMPLETE));

        final int prefetchCapacity = Integer.parseInt(properties.getProperty(ClientProperties.CLIENT_TOPIC_SUBSCRIPTION_PREFETCH_CAPACITY));
        subscriptionManager = new SubscriptionManager(
                this,
                numExecutionThreads,
                autoCompleteTasks,
                prefetchCapacity,
                dataFrameReceiveBuffer.openSubscription("task-acquisition"));
        transport.registerChannelListener(subscriptionManager);

        topologyManager = new ClientTopologyManager(transport, objectMapper, contactPoint);
        commandManager = new ClientCommandManager(transport, topologyManager);
    }

    @Override
    public void connect()
    {
        if (!connected)
        {
            commandManagerActorReference = transportActorScheduler.schedule(commandManager);
            topologyManagerActorReference = transportActorScheduler.schedule(topologyManager);

            subscriptionManager.start();

            connected = true;
        }
    }

    @Override
    public void disconnect()
    {
        if (connected)
        {
            subscriptionManager.closeAllSubscriptions();
            subscriptionManager.stop();

            topologyManagerActorReference.close();
            topologyManagerActorReference = null;

            commandManagerActorReference.close();
            commandManagerActorReference = null;

            transport.closeAllChannels().join();

            connected = false;
        }
    }

    @Override
    public void close()
    {
        disconnect();

        subscriptionManager.close();

        try
        {
            transport.close();
        }
        catch (final Exception e)
        {
            e.printStackTrace();
        }

        try
        {
            dataFrameReceiveBuffer.close();
        }
        catch (final Exception e)
        {
            e.printStackTrace();
        }

        try
        {
            sendBuffer.close();
        }
        catch (final Exception e)
        {
            e.printStackTrace();
        }

        transportActorScheduler.close();
    }

    @Override
    public RequestTopologyCmd requestTopology()
    {
        return new RequestTopologyCmdImpl(commandManager, objectMapper);
    }

    @Override
    public TaskTopicClientImpl taskTopic(final String topicName, final int partitionId)
    {
        ensureNotNullOrEmpty("topic name", topicName);
        ensureGreaterThanOrEqual("partition id", partitionId, 0);
        return new TaskTopicClientImpl(this, topicName, partitionId);
    }

    @Override
    public WorkflowTopicClient workflowTopic(final String topicName, final int partitionId)
    {
        ensureNotNullOrEmpty("topic name", topicName);
        ensureGreaterThanOrEqual("partition id", partitionId, 0);
        return new WorkflowTopicClientImpl(this, topicName, partitionId);
    }

    @Override
    public TopicClientImpl topic(final String topicName, final int partitionId)
    {
        ensureNotNullOrEmpty("topic name", topicName);
        ensureGreaterThanOrEqual("partition id", partitionId, 0);
        return new TopicClientImpl(this, topicName, partitionId);
    }

    public ClientCommandManager getCommandManager()
    {
        return commandManager;
    }

    public ClientTopologyManager getTopologyManager()
    {
        return topologyManager;
    }

    public SubscriptionManager getSubscriptionManager()
    {
        return subscriptionManager;
    }

    public ObjectMapper getObjectMapper()
    {
        return objectMapper;
    }

    public Properties getInitializationProperties()
    {
        return initializationProperties;
    }

    public ClientTransport getTransport()
    {
        return transport;
    }
}
