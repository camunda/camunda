/**
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
package io.zeebe.test.broker.protocol.clientapi;

import java.util.stream.Stream;

import org.agrona.DirectBuffer;
import org.junit.rules.ExternalResource;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.SubscribedEventDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;

public class ClientApiRule extends ExternalResource
{
    public static final String DEFAULT_TOPIC_NAME = "default-topic";
    public static final int DEFAULT_PARTITION_ID = 0;

    protected ClientTransport transport;
    protected Dispatcher sendBuffer;

    protected final int port = 51015;
    protected final String host = "localhost";
    protected final SocketAddress brokerAddress;
    protected RemoteAddress streamAddress;

    protected MsgPackHelper msgPackHelper;
    protected RawMessageCollector incomingMessageCollector;

    public ClientApiRule()
    {
        this.brokerAddress = new SocketAddress(host, port);
    }

    @Override
    protected void before() throws Throwable
    {

        final ActorScheduler scheduler = ActorSchedulerBuilder.createDefaultScheduler();

        sendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(32 * 1024 * 1024)
            .subscriptions("sender")
            .actorScheduler(scheduler)
            .build();

        incomingMessageCollector = new RawMessageCollector();

        transport = Transports.newClientTransport()
                .inputListener(incomingMessageCollector)
                .scheduler(scheduler)
                .requestPoolSize(128)
                .sendBuffer(sendBuffer)
                .build();

        msgPackHelper = new MsgPackHelper();
        streamAddress = transport.registerRemoteAddress(brokerAddress);
    }

    @Override
    protected void after()
    {

        if (sendBuffer != null)
        {
            sendBuffer.close();
        }

        if (transport != null)
        {
            transport.close();
        }
    }

    public ExecuteCommandRequestBuilder createCmdRequest()
    {
        return new ExecuteCommandRequestBuilder(transport.getOutput(), streamAddress, msgPackHelper);
    }

    public ControlMessageRequestBuilder createControlMessageRequest()
    {
        return new ControlMessageRequestBuilder(transport.getOutput(), streamAddress, msgPackHelper);
    }

    public ClientApiRule moveMessageStreamToTail()
    {
        incomingMessageCollector.moveToTail();
        return this;
    }

    public ClientApiRule moveMessageStreamToHead()
    {
        incomingMessageCollector.moveToHead();
        return this;
    }

    public int numSubscribedEventsAvailable()
    {
        return (int) incomingMessageCollector.getNumMessagesFulfilling(this::isSubscribedEvent);
    }

    public TestTopicClient topic()
    {
        return topic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);
    }

    public TestTopicClient topic(final String topicName, final int partitionId)
    {
        return new TestTopicClient(this, topicName, partitionId);
    }

    public ExecuteCommandRequest openTopicSubscription(final String name, final long startPosition)
    {
        return openTopicSubscription(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID, name, startPosition);
    }

    public ExecuteCommandRequest openTopicSubscription(final String topicName, final int partitionId, final String name, final long startPosition)
    {
        return createCmdRequest()
            .topicName(topicName)
            .partitionId(partitionId)
            .eventTypeSubscriber()
            .command()
                .put("startPosition", startPosition)
                .put("name", name)
                .put("eventType", "SUBSCRIBE")
                .done()
            .send();
    }

    public ControlMessageRequest closeTopicSubscription(long subscriberKey)
    {
        return createControlMessageRequest()
                .messageType(ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
                .data()
                .put("topicName", DEFAULT_TOPIC_NAME)
                .put("partitionId", DEFAULT_PARTITION_ID)
                .put("subscriberKey", subscriberKey)
                .done()
                .send();
    }

    public ControlMessageRequest openTaskSubscription(final String type)
    {
        return openTaskSubscription(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID, type);
    }

    public ControlMessageRequest openTaskSubscription(final String topicName, final int partitionId, final String type)
    {
        return createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .data()
                .put("topicName", topicName)
                .put("partitionId", partitionId)
                .put("taskType", type)
                .put("lockDuration", 1000L)
                .put("lockOwner", "test")
                .put("credits", 10)
                .done()
            .send();
    }

    public Stream<RawMessage> incomingMessages()
    {
        return Stream.generate(incomingMessageCollector);
    }

    /**
     * @return an infinite stream of received subscribed events; make sure to use short-circuiting operations
     *   to reduce it to a finite stream
     */
    public Stream<SubscribedEvent> subscribedEvents()
    {
        return incomingMessages().filter(this::isSubscribedEvent)
                .map(this::asSubscribedEvent);
    }

    public Stream<RawMessage> commandResponses()
    {
        return incomingMessages().filter(this::isCommandResponse);
    }

    public void interruptAllChannels()
    {
        transport.interruptAllChannels();
    }

    protected SubscribedEvent asSubscribedEvent(RawMessage message)
    {
        final SubscribedEvent event = new SubscribedEvent(message);
        event.wrap(message.getMessage(), 0, message.getMessage().capacity());
        return event;
    }

    protected boolean isCommandResponse(RawMessage message)
    {
        return message.isResponse() &&
                isMessageOfType(message.getMessage(), ExecuteCommandResponseDecoder.TEMPLATE_ID);
    }

    protected boolean isSubscribedEvent(RawMessage message)
    {
        return message.isMessage() &&
                isMessageOfType(message.getMessage(), SubscribedEventDecoder.TEMPLATE_ID);

    }

    protected boolean isMessageOfType(DirectBuffer message, int type)
    {
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(message, 0);

        return headerDecoder.templateId() == type;
    }
}
