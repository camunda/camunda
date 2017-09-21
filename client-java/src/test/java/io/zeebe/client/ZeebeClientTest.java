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
package io.zeebe.client;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.TransportListener;

public class ZeebeClientTest
{
    @Rule
    public StubBrokerRule broker = new StubBrokerRule();

    protected ZeebeClientImpl client;

    @Before
    public void setUp()
    {
        client = (ZeebeClientImpl) ZeebeClient.create(new Properties());
        broker.stubTopicSubscriptionApi(0);
    }

    @After
    public void tearDown()
    {
        client.close();
    }

    @Test
    public void shouldCloseAllConnectionsOnDisconnect() throws Exception
    {
        // given
        final ClientTransport clientTransport = client.getTransport();
        final LoggingChannelListener channelListener = new LoggingChannelListener();
        clientTransport.registerChannelListener(channelListener);

        final TopicSubscription subscription = openSubscription();

        // when
        client.disconnect();

        // then
        assertThat(subscription.isClosed()).isTrue();
        waitUntil(() -> channelListener.connectionState.size() == 2); // listener invocation on close is asynchronous
        assertThat(channelListener.connectionState).containsExactly(ConnectionState.CONNECTED, ConnectionState.CLOSED);

        // and there is no reconnect attempt within the next second
        Thread.sleep(1000L);

        assertThat(subscription.isClosed()).isTrue();
        assertThat(channelListener.connectionState).containsExactly(ConnectionState.CONNECTED, ConnectionState.CLOSED);
    }

    @Test
    public void shouldEstablishNewConnectionsAfterDisconnect()
    {
        // given
        final ClientTransport clientTransport = client.getTransport();
        final LoggingChannelListener channelListener = new LoggingChannelListener();
        clientTransport.registerChannelListener(channelListener);

        openSubscription();

        // when
        client.disconnect();

        // then
        final TopicSubscription newSubscription = openSubscription();

        assertThat(newSubscription.isOpen()).isTrue();
        waitUntil(() -> channelListener.connectionState.size() == 3); // listener invocation is asynchronous
        assertThat(channelListener.connectionState).containsExactly(
                ConnectionState.CONNECTED,
                ConnectionState.CLOSED,
                ConnectionState.CONNECTED);


    }

    protected TopicSubscription openSubscription()
    {
        return client.topics().newSubscription(ClientApiRule.DEFAULT_TOPIC_NAME)
                .handler(e ->
                {
                })
                .name("foo")
                .startAtHeadOfTopic()
                .open();
    }

    protected static class LoggingChannelListener implements TransportListener
    {

        protected List<ConnectionState> connectionState = new CopyOnWriteArrayList<>();

        @Override
        public void onConnectionEstablished(RemoteAddress remoteAddress)
        {
            connectionState.add(ConnectionState.CONNECTED);
        }

        @Override
        public void onConnectionClosed(RemoteAddress remoteAddress)
        {
            connectionState.add(ConnectionState.CLOSED);
        }
    }

    protected enum ConnectionState
    {
        CONNECTED,
        CLOSED;
    }
}
