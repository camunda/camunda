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
package io.zeebe.gateway;

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.subscription.TopicSubscription;
import io.zeebe.gateway.cmd.ClientCommandRejectedException;
import io.zeebe.gateway.cmd.ClientException;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.gateway.impl.clustering.ClientTopologyManager;
import io.zeebe.gateway.impl.event.JobEventImpl;
import io.zeebe.gateway.util.Events;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandResponseBuilder;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.TransportListener;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

public class ZeebeClientTest {
  @Rule public StubBrokerRule broker = new StubBrokerRule();

  @Rule public ExpectedException exception = ExpectedException.none();

  @Rule public AutoCloseableRule closeables = new AutoCloseableRule();

  @Rule public TestName testContext = new TestName();

  protected ZeebeClientImpl client;

  private int clientMaxRequests;
  private String topic = DEFAULT_TOPIC;

  @Before
  public void setUp() {
    this.clientMaxRequests = 128;
    final Properties properties = new Properties();
    properties.setProperty(ClientProperties.REQUEST_TIMEOUT_SEC, "3");
    properties.setProperty(ClientProperties.REQUEST_BLOCKTIME_MILLIS, "250");

    client = (ZeebeClientImpl) ZeebeClient.newClientBuilder().withProperties(properties).build();
    broker.stubTopicSubscriptionApi(0);
  }

  @After
  public void tearDown() {
    client.close();
  }

  @Test
  public void shouldCloseAllConnectionsOnClose() throws Exception {
    // given
    final ServerTransport serverTransport = broker.getTransport();

    final TopicSubscription subscription = openSubscription();
    final LoggingChannelListener channelListener = new LoggingChannelListener();
    serverTransport.registerChannelListener(channelListener).join();

    // when
    client.close();

    // then
    assertThat(subscription.isClosed()).isTrue();
    TestUtil.waitUntil(
        () ->
            channelListener.connectionState.size()
                == 2); // listener invocation on close is asynchronous
    assertThat(channelListener.connectionState)
        .containsExactly(ConnectionState.CLOSED, ConnectionState.CLOSED);
  }

  @Test
  public void shouldEstablishNewConnectionsAfterDisconnect() {
    // given
    final ClientTransport clientTransport = client.getTransport();

    // ensuring an open connection
    client.newTopicsRequest().send().join();

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
    TestUtil.waitUntil(
        () ->
            channelListener.connectionState.contains(
                ConnectionState.CONNECTED)); // listener invocation is asynchronous
    assertThat(channelListener.connectionState).last().isSameAs(ConnectionState.CONNECTED);
  }

  @Test
  public void shouldCloseIdempotently() {
    // given
    client.close();

    // when
    client.close();

    // then
    assertThat("this code has been reached, i.e. the second close does not block infinitely")
        .isNotNull();
  }

  @Test
  public void shouldSendAsyncRequestUpToPoolCapacity() {
    // given
    broker.clearTopology();
    broker.addSystemTopic();
    broker.addTopic(topic, 0);
    broker.addTopic(topic, 1);

    stubJobResponse();

    // when then
    for (int i = 0; i < clientMaxRequests; i++) {
      client.topicClient().jobClient().newCreateCommand().jobType("foo").send().join();
    }
  }

  @Test
  public void shouldReleaseRequestsOnGet() throws InterruptedException, ExecutionException {
    // given

    broker.clearTopology();
    broker.addSystemTopic();
    broker.addTopic(topic, 0);
    broker.addTopic(topic, 1);

    stubJobResponse();

    final List<ActorFuture<JobEvent>> futures = new ArrayList<>();
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<JobEvent> future =
          client.topicClient().jobClient().newCreateCommand().jobType("bar").send();

      futures.add(future);
    }

    // when
    for (ActorFuture<JobEvent> future : futures) {
      future.join();
    }

    // then
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<JobEvent> future =
          client.topicClient().jobClient().newCreateCommand().jobType("bar").send();

      futures.add(future);
    }
  }

  @Test
  public void shouldReleaseRequestsOnTimeout() {
    // given
    final JobEvent baseEvent = Events.exampleJob();

    broker.onExecuteCommandRequest(ValueType.JOB, JobIntent.COMPLETE).doNotRespond();

    // given
    final List<ActorFuture<JobEvent>> futures = new ArrayList<>();
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<JobEvent> future =
          client.topicClient().jobClient().newCompleteCommand(baseEvent).send();

      futures.add(future);
    }

    // when
    for (ActorFuture<JobEvent> future : futures) {
      try {
        future.join();
        fail("exception expected");
      } catch (Exception e) {
        // expected
      }
    }

    // then
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<JobEvent> future =
          client.topicClient().jobClient().newCompleteCommand(baseEvent).send();

      futures.add(future);
    }
  }

  @Test
  public void shouldDistributeNewEntitiesRoundRobin() {
    // given
    broker.clearTopology();
    broker.addSystemTopic();
    broker.addTopic(topic, 0);
    broker.addTopic(topic, 1);

    stubJobResponse();

    final JobClient jobClient = client.topicClient().jobClient();

    // when
    final JobEvent job1 = jobClient.newCreateCommand().jobType("bar").send().join();
    final JobEvent job2 = jobClient.newCreateCommand().jobType("bar").send().join();
    final JobEvent job3 = jobClient.newCreateCommand().jobType("bar").send().join();
    final JobEvent job4 = jobClient.newCreateCommand().jobType("bar").send().join();

    // then
    assertThat(Arrays.asList(job1, job2, job3, job4))
        .extracting("metadata.partitionId")
        .containsExactlyInAnyOrder(0, 1, 0, 1);
  }

  @Test
  public void shouldThrowExceptionOnTimeout() {
    // given
    final JobEventImpl baseEvent = Events.exampleJob();

    broker.onExecuteCommandRequest(ValueType.JOB, JobIntent.COMPLETE).doNotRespond();

    // then
    exception.expect(ClientException.class);
    exception.expectMessage(
        "Request timed out (PT3S). "
            + "Request was: [ topic = "
            + DEFAULT_TOPIC
            + ", partition = 1, value type = JOB, command = COMPLETE ]");

    // when
    client.topicClient().jobClient().newCompleteCommand(baseEvent).send().join();
  }

  @Test
  public void shouldIncludeCallingFrameInExceptionStacktrace() {
    // given
    final JobEventImpl baseEvent = Events.exampleJob();

    broker.jobs().registerCompleteCommand(ExecuteCommandResponseBuilder::rejection);

    // when
    try {
      client.topicClient().jobClient().newCompleteCommand(baseEvent).send().join();

      fail("should throw exception");
    } catch (ClientCommandRejectedException e) {
      // then
      assertThat(e.getStackTrace())
          .anySatisfy(
              frame -> {
                assertThat(frame.getClassName()).isEqualTo(this.getClass().getName());
                assertThat(frame.getMethodName()).isEqualTo(testContext.getMethodName());
              });
    }
  }

  @Test
  public void shouldIncludeCallingFrameInExceptionStacktraceOnAsyncRootCause() throws Exception {
    // given
    final JobEventImpl baseEvent = Events.exampleJob();

    broker.jobs().registerCompleteCommand(r -> r.rejection());

    // when
    try {
      client.topicClient().jobClient().newCompleteCommand(baseEvent).send().join();

      fail("should throw exception");
    } catch (Exception e) {
      // then
      assertThat(e.getStackTrace())
          .anySatisfy(
              frame -> {
                assertThat(frame.getClassName()).isEqualTo(this.getClass().getName());
                assertThat(frame.getMethodName()).isEqualTo(testContext.getMethodName());
              });
    }
  }

  @Test
  public void shouldThrottleTopologyRefreshRequestsWhenTopicPartitionCannotBeDetermined() {
    // when
    final long start = System.currentTimeMillis();
    assertThatThrownBy(
        () -> client.topicClient().jobClient().newCreateCommand().jobType("baz").send().join());
    final long requestDuration = System.currentTimeMillis() - start;

    // then
    final long actualTopologyRequests =
        broker
            .getReceivedControlMessageRequests()
            .stream()
            .filter(r -> r.messageType() == ControlMessageType.REQUEST_TOPOLOGY)
            .count();

    // +4 (one for the extra request when client is started)
    final long expectedMaximumTopologyRequests =
        (requestDuration / ClientTopologyManager.MIN_REFRESH_INTERVAL_MILLIS.toMillis()) + 4;

    assertThat(actualTopologyRequests).isLessThanOrEqualTo(expectedMaximumTopologyRequests);
  }

  @Test
  public void shouldThrottleTopologyRefreshRequestsWhenPartitionLeaderCannotBeDetermined() {
    // given
    final int nonExistingPartition = 999;

    final JobEventImpl jobEvent = Events.exampleJob();
    jobEvent.setPartitionId(nonExistingPartition);

    // when
    final long start = System.currentTimeMillis();
    assertThatThrownBy(
        () -> client.topicClient().jobClient().newCompleteCommand(jobEvent).send().join());
    final long requestDuration = System.currentTimeMillis() - start;

    // then
    final long actualTopologyRequests =
        broker
            .getReceivedControlMessageRequests()
            .stream()
            .filter(r -> r.messageType() == ControlMessageType.REQUEST_TOPOLOGY)
            .count();

    // +4 (one for the extra request when client is started)
    final long expectedMaximumTopologyRequests =
        (requestDuration / ClientTopologyManager.MIN_REFRESH_INTERVAL_MILLIS.toMillis()) + 4;

    assertThat(actualTopologyRequests).isLessThanOrEqualTo(expectedMaximumTopologyRequests);
  }

  @Test
  public void shouldCreateClientWithFluentBuilder() {
    // given
    final String contactPoint = "foo:123";
    final int numManagementThreads = 2;
    final int numSubscriptionThreads = 3;
    final Duration requestBlockTime = Duration.ofSeconds(4);
    final Duration requestTimeout = Duration.ofSeconds(5);
    final int sendBufferSize = 6;
    final Duration tcpChannelKeepAlivePeriod = Duration.ofSeconds(7);
    final int topicSubscriptionBufferSize = 8;
    final int jobSubscriptionBufferSize = 9;

    // when
    final ZeebeClient client =
        ZeebeClient.newClientBuilder()
            .brokerContactPoint(contactPoint)
            .numManagementThreads(numManagementThreads)
            .numSubscriptionExecutionThreads(numSubscriptionThreads)
            .requestBlocktime(requestBlockTime)
            .requestTimeout(requestTimeout)
            .sendBufferSize(sendBufferSize)
            .tcpChannelKeepAlivePeriod(tcpChannelKeepAlivePeriod)
            .defaultTopicSubscriptionBufferSize(topicSubscriptionBufferSize)
            .defaultJobSubscriptionBufferSize(jobSubscriptionBufferSize)
            .build();
    closeables.manage(client);

    // then
    final ZeebeClientConfiguration configuration = client.getConfiguration();
    assertThat(configuration.getBrokerContactPoint()).isEqualTo(contactPoint);
    assertThat(configuration.getNumManagementThreads()).isEqualTo(numManagementThreads);
    assertThat(configuration.getNumSubscriptionExecutionThreads())
        .isEqualTo(numSubscriptionThreads);
    assertThat(configuration.getRequestBlocktime()).isEqualTo(requestBlockTime);
    assertThat(configuration.getRequestTimeout()).isEqualTo(requestTimeout);
    assertThat(configuration.getSendBufferSize()).isEqualTo(sendBufferSize);
    assertThat(configuration.getTcpChannelKeepAlivePeriod()).isEqualTo(tcpChannelKeepAlivePeriod);
    assertThat(configuration.getDefaultTopicSubscriptionBufferSize())
        .isEqualTo(topicSubscriptionBufferSize);
    assertThat(configuration.getDefaultJobSubscriptionBufferSize())
        .isEqualTo(jobSubscriptionBufferSize);
  }

  @Test
  public void shouldThrowExceptionOnRejectionWithBadValue() {
    // given
    final JobEventImpl baseEvent = Events.exampleJob();

    broker.jobs().registerCompleteCommand(b -> b.rejection(RejectionType.BAD_VALUE, "foo"));

    final String updatedPayload = "{\"fruit\":\"cherry\"}";

    // then
    exception.expect(ClientCommandRejectedException.class);
    exception.expectMessage(
        "Command (COMPLETE) for event with key 79 was rejected. It has an invalid value. foo");

    // when
    client
        .topicClient()
        .jobClient()
        .newCompleteCommand(baseEvent)
        .payload(updatedPayload)
        .send()
        .join();
  }

  @Test
  public void shouldThrowExceptionOnRejectionWhenNotApplicable() {
    // given
    final JobEventImpl baseEvent = Events.exampleJob();

    broker.jobs().registerCompleteCommand(b -> b.rejection(RejectionType.NOT_APPLICABLE, "foo"));

    final String updatedPayload = "{\"fruit\":\"cherry\"}";

    // then
    exception.expect(ClientCommandRejectedException.class);
    exception.expectMessage(
        "Command (COMPLETE) for event with key 79 was rejected. It is not applicable in the current state. foo");

    // when
    client
        .topicClient()
        .jobClient()
        .newCompleteCommand(baseEvent)
        .payload(updatedPayload)
        .send()
        .join();
  }

  @Test
  public void shouldThrowExceptionOnRejectionOnProcessingError() {
    // given
    final JobEventImpl baseEvent = Events.exampleJob();

    broker.jobs().registerCompleteCommand(b -> b.rejection(RejectionType.PROCESSING_ERROR, "foo"));

    final String updatedPayload = "{\"fruit\":\"cherry\"}";

    // then
    exception.expect(ClientCommandRejectedException.class);
    exception.expectMessage(
        "Command (COMPLETE) for event with key 79 was rejected. "
            + "The broker could not process it for internal reasons. foo");

    // when
    client
        .topicClient()
        .jobClient()
        .newCompleteCommand(baseEvent)
        .payload(updatedPayload)
        .send()
        .join();
  }

  protected TopicSubscription openSubscription() {
    return client
        .topicClient()
        .newSubscription()
        .name("foo")
        .recordHandler(r -> {})
        .startAtHeadOfTopic()
        .open();
  }

  protected static class LoggingChannelListener implements TransportListener {

    protected List<ConnectionState> connectionState = new CopyOnWriteArrayList<>();

    @Override
    public void onConnectionEstablished(RemoteAddress remoteAddress) {
      connectionState.add(ConnectionState.CONNECTED);
    }

    @Override
    public void onConnectionClosed(RemoteAddress remoteAddress) {
      connectionState.add(ConnectionState.CLOSED);
    }
  }

  protected void stubJobResponse() {
    broker.jobs().registerCreateCommand();
    broker.jobs().registerCompleteCommand();
  }

  protected enum ConnectionState {
    CONNECTED,
    CLOSED
  }
}
