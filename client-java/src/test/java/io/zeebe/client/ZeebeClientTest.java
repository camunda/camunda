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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.event.impl.TaskEventImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.util.Events;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.TransportListener;

public class ZeebeClientTest
{
    @Rule
    public StubBrokerRule broker = new StubBrokerRule();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public TestName testContext = new TestName();

    protected ZeebeClientImpl client;

    private int clientMaxRequests;

    @Before
    public void setUp()
    {
        this.clientMaxRequests = 128;
        final Properties properties = new Properties();
        properties.setProperty(ClientProperties.CLIENT_REQUEST_TIMEOUT_SEC, "3");
        properties.setProperty(ClientProperties.CLIENT_REQUEST_BLOCKTIME_MILLIS, "250");
        properties.setProperty(ClientProperties.CLIENT_MAXREQUESTS, String.valueOf(clientMaxRequests));


        client = (ZeebeClientImpl) ZeebeClient.create(properties);
        broker.stubTopicSubscriptionApi(0);
    }

    @After
    public void tearDown()
    {
        client.close();
    }

    @Test
    public void shouldCloseAllConnectionsOnClose() throws Exception
    {
        // given
        final ServerTransport serverTransport = broker.getTransport();

        final TopicSubscription subscription = openSubscription();
        final LoggingChannelListener channelListener = new LoggingChannelListener();
        serverTransport.registerChannelListener(channelListener).join();

        // when
        client.close();

        // then
        assertThat(subscription.isClosed()).isTrue();
        waitUntil(() -> channelListener.connectionState.size() == 1); // listener invocation on close is asynchronous
        assertThat(channelListener.connectionState).containsExactly(ConnectionState.CLOSED);
    }

    @Test
    public void shouldEstablishNewConnectionsAfterDisconnect()
    {
        // given
        final ClientTransport clientTransport = client.getTransport();

        // ensuring an open connection
        client.requestTopology().execute();

        final LoggingChannelListener channelListener = new LoggingChannelListener();
        clientTransport.registerChannelListener(channelListener).join();

        // when
        broker.closeTransport();
        System.out.println("Broker transport closed");
        broker.bindTransport();
        System.out.println("Broker transport bound");

        // then
        final TopicSubscription newSubscription = openSubscription();

        assertThat(newSubscription.isOpen()).isTrue();
        waitUntil(() -> channelListener.connectionState.contains(ConnectionState.CONNECTED)); // listener invocation is asynchronous
        assertThat(channelListener.connectionState)
            .last()
            .isSameAs(ConnectionState.CONNECTED);
    }

    @Test
    public void shouldCloseIdempotently()
    {
        // given
        client.close();

        // when
        client.close();

        // then
        assertThat("this code has been reached, i.e. the second close does not block infinitely").isNotNull();
    }

    @Test
    public void shouldSendAsyncRequestUpToPoolCapacity()
    {
        // given
        final String topic = "foo";

        broker.clearTopology();
        broker.addSystemTopic();
        broker.addTopic(topic, 0);
        broker.addTopic(topic, 1);

        stubTaskResponse();

        // when then
        for (int i = 0; i < clientMaxRequests; i++)
        {
            client.tasks().create(topic, "bar").executeAsync();
        }

    }

    @Test
    public void shouldReleaseRequestsOnGet() throws InterruptedException, ExecutionException
    {
        // given
        final String topic = "foo";

        broker.clearTopology();
        broker.addSystemTopic();
        broker.addTopic(topic, 0);
        broker.addTopic(topic, 1);

        stubTaskResponse();

        final List<Future<TaskEvent>> futures = new ArrayList<>();
        for (int i = 0; i < clientMaxRequests; i++)
        {
            futures.add(client.tasks().create(topic, "bar").executeAsync());
        }

        // when
        for (Future<TaskEvent> future : futures)
        {
            future.get();
        }

        // then
        for (int i = 0; i < clientMaxRequests; i++)
        {
            futures.add(client.tasks().create(topic, "bar").executeAsync());
        }
    }

    @Test
    public void shouldReleaseRequestsOnTimeout()
    {
        // given
        final TaskEventImpl baseEvent = Events.exampleTask();

        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .doNotRespond();

        // given
        final List<Future<TaskEvent>> futures = new ArrayList<>();
        for (int i = 0; i < clientMaxRequests; i++)
        {
            futures.add(client.tasks().complete(baseEvent).executeAsync());
        }

        // when
        for (Future<TaskEvent> future : futures)
        {
            try
            {
                future.get();
                fail("exception expected");
            }
            catch (Exception e)
            {
                // expected
            }
        }

        // then
        for (int i = 0; i < clientMaxRequests; i++)
        {
            futures.add(client.tasks().complete(baseEvent).executeAsync());
        }

    }

    @Test
    public void shouldBlockOnAsyncRequestsPastPoolCapacity()
    {
        // given
        final String topic = "foo";

        broker.clearTopology();
        broker.addSystemTopic();
        broker.addTopic(topic, 0);
        broker.addTopic(topic, 1);

        stubTaskResponse();

        for (int i = 0; i < clientMaxRequests; i++)
        {
            client.tasks().create(topic, "bar").executeAsync();
        }

        try
        {
            client.tasks().create(topic, "bar").executeAsync();
            fail("should throw exception");
        }
        catch (Exception e)
        {
            // expected
            assertThat(e.getMessage()).startsWith("Could not send request");
        }

    }

    @Test
    public void shouldDistributeNewEntitiesRoundRobin()
    {
        // given
        final String topic = "foo";

        broker.clearTopology();
        broker.addSystemTopic();
        broker.addTopic(topic, 0);
        broker.addTopic(topic, 1);

        stubTaskResponse();

        // when
        final TaskEvent task1 = client.tasks().create(topic, "bar").execute();
        final TaskEvent task2 = client.tasks().create(topic, "bar").execute();
        final TaskEvent task3 = client.tasks().create(topic, "bar").execute();
        final TaskEvent task4 = client.tasks().create(topic, "bar").execute();

        // then
        assertThat(Arrays.asList(task1, task2, task3, task4))
            .extracting("metadata.partitionId")
            .containsExactlyInAnyOrder(0, 1, 0, 1);
    }

    @Test
    public void shouldFailRequestToNonExistingTopic()
    {
        // given
        final String topic = "foo";

        broker.clearTopology();
        broker.addSystemTopic();

        stubTaskResponse();

        // then
        exception.expect(ClientException.class);
        exception.expectMessage("Cannot determine target partition for request. " +
                "Request was: [ topic = foo, partition = any, event type = TASK, state = CREATE ]");

        // when
        client.tasks().create(topic, "bar").execute();
    }

    @Test
    public void shouldThrowExceptionOnTimeout()
    {
        // given
        final TaskEventImpl baseEvent = Events.exampleTask();

        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .doNotRespond();

        // then
        exception.expect(ClientException.class);
        exception.expectMessage("Request timed out (PT3S). " +
                "Request was: [ topic = default-topic, partition = 99, event type = TASK, state = COMPLETE ]");

        // when
        client.tasks().complete(baseEvent).execute();
    }

    @Test
    public void shouldIncludeCallingFrameInExceptionStacktrace()
    {
        // given
        final TaskEventImpl baseEvent = Events.exampleTask();

        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .respondWith()
            .key(r -> r.key())
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "COMPLETE_REJECTED")
              .done()
            .register();

        // when
        try
        {
            client.tasks()
                .complete(baseEvent)
                .execute();
            fail("should throw exception");
        }
        catch (ClientCommandRejectedException e)
        {
            // then
            assertThat(e.getStackTrace()).anySatisfy(frame ->
            {
                assertThat(frame.getClassName()).isEqualTo(this.getClass().getName());
                assertThat(frame.getMethodName()).isEqualTo(testContext.getMethodName());
            });
        }
    }

    @Test
    public void shouldIncludeCallingFrameInExceptionStacktraceOnAsyncRootCause() throws Exception
    {
        // given
        final TaskEventImpl baseEvent = Events.exampleTask();

        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
              .respondWith()
              .key(r -> r.key())
              .value()
              .allOf((r) -> r.getCommand())
              .put("state", "COMPLETE_REJECTED")
              .done()
              .register();

        // when
        try
        {
            client.tasks()
                  .complete(baseEvent)
                  .executeAsync().get();

            fail("should throw exception");
        }
        catch (ExecutionException e)
        {
            // then
            assertThat(e.getStackTrace()).anySatisfy(frame ->
            {
                assertThat(frame.getClassName()).isEqualTo(this.getClass().getName());
                assertThat(frame.getMethodName()).isEqualTo(testContext.getMethodName());
            });
        }
    }

    @Test
    public void shouldThrottleTopologyRefreshRequestsWhenTopicPartitionCannotBeDetermined()
    {
        // when
        assertThatThrownBy(() ->
        {
            client.tasks()
                .create("non-existing-topic", "baz")
                .execute();
        });

        // +4 (one for the extra request when client is started)
        final Duration requestTimeout = client.getConfiguration().getRequestTimeout();
        final long requestTimeoutMs = requestTimeout.toMillis();
        final long expectedMaximumTopologyRequests =
                (requestTimeoutMs / ClientTopologyManager.MIN_REFRESH_INTERVAL_MILLIS.toMillis()) + 4;
        final long actualTopologyRequests = broker
            .getReceivedControlMessageRequests()
            .stream()
            .filter(r -> r.messageType() == ControlMessageType.REQUEST_TOPOLOGY)
            .count();

        assertThat(actualTopologyRequests).isLessThanOrEqualTo(expectedMaximumTopologyRequests);
    }

    @Test
    public void shouldThrottleTopologyRefreshRequestsWhenPartitionLeaderCannotBeDetermined()
    {
        // when
        final int nonExistingPartition = 999;

        final TaskEventImpl taskEvent = new TaskEventImpl("CREATED", new MsgPackConverter());
        taskEvent.setPartitionId(nonExistingPartition);

        assertThatThrownBy(() ->
        {
            client.tasks().complete(taskEvent).execute();
        });

        // +4 (one for the extra request when client is started)
        final Duration requestTimeout = client.getConfiguration().getRequestTimeout();
        final long requestTimeoutMs = requestTimeout.toMillis();
        final long expectedMaximumTopologyRequests = (requestTimeoutMs / ClientTopologyManager.MIN_REFRESH_INTERVAL_MILLIS.toMillis()) + 4;
        final long actualTopologyRequests = broker
            .getReceivedControlMessageRequests()
            .stream()
            .filter(r -> r.messageType() == ControlMessageType.REQUEST_TOPOLOGY)
            .count();

        assertThat(actualTopologyRequests).isLessThanOrEqualTo(expectedMaximumTopologyRequests);
    }

    @Test
    public void shouldCreateClientWithFluentBuilder()
    {
        // given
        final String contactPoint = "foo:123";
        final int maxRequests = 1;
        final int numManagementThreads = 2;
        final int numSubscriptionThreads = 3;
        final Duration requestBlockTime = Duration.ofSeconds(4);
        final Duration requestTimeout = Duration.ofSeconds(5);
        final int sendBufferSize = 6;
        final Duration tcpChannelKeepAlivePeriod = Duration.ofSeconds(7);
        final int topicSubscriptionPrefetchCapacity = 8;

        // when
        final ZeebeClient client = ZeebeClient.newClient()
                .brokerContactPoint(contactPoint)
                .maxRequests(maxRequests)
                .numManagementThreads(numManagementThreads)
                .numSubscriptionExecutionThreads(numSubscriptionThreads)
                .requestBlocktime(requestBlockTime)
                .requestTimeout(requestTimeout)
                .sendBufferSize(sendBufferSize)
                .tcpChannelKeepAlivePeriod(tcpChannelKeepAlivePeriod)
                .topicSubscriptionPrefetchCapacity(topicSubscriptionPrefetchCapacity)
                .create();

        // then
        final ZeebeClientConfiguration configuration = client.getConfiguration();
        assertThat(configuration.getBrokerContactPoint()).isEqualTo(contactPoint);
        assertThat(configuration.getMaxRequests()).isEqualTo(maxRequests);
        assertThat(configuration.getNumManagementThreads()).isEqualTo(numManagementThreads);
        assertThat(configuration.getNumSubscriptionExecutionThreads()).isEqualTo(numSubscriptionThreads);
        assertThat(configuration.getRequestBlocktime()).isEqualTo(requestBlockTime);
        assertThat(configuration.getRequestTimeout()).isEqualTo(requestTimeout);
        assertThat(configuration.getSendBufferSize()).isEqualTo(sendBufferSize);
        assertThat(configuration.getTcpChannelKeepAlivePeriod()).isEqualTo(tcpChannelKeepAlivePeriod);
        assertThat(configuration.getTopicSubscriptionPrefetchCapacity()).isEqualTo(topicSubscriptionPrefetchCapacity);
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

    protected void stubTaskResponse()
    {
        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "CREATE")
            .respondWith()
            .key(123)
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "CREATED")
              .put("lockTime", Protocol.INSTANT_NULL_VALUE)
              .put("lockOwner", "")
              .done()
            .register();
    }

    protected enum ConnectionState
    {
        CONNECTED,
        CLOSED;
    }
}
