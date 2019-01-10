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
package io.zeebe.gateway.broker;

import static io.zeebe.protocol.clientapi.ControlMessageType.REQUEST_TOPOLOGY;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.CoreMatchers.containsString;

import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.response.BrokerError;
import io.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.gateway.impl.data.MsgPackConverter;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.brokerapi.ControlMessageRequest;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandResponseBuilder;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.TransportListener;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

public class BrokerClientTest {

  private static final DirectBuffer EMPTY_PAYLOAD =
      new UnsafeBuffer(new MsgPackConverter().convertToMsgPack("{}"));

  @Rule public StubBrokerRule broker = new StubBrokerRule();

  @Rule public AutoCloseableRule closeables = new AutoCloseableRule();
  @Rule public ExpectedException exception = ExpectedException.none();
  @Rule public TestName testContext = new TestName();

  private final int clientMaxRequests = 128;
  private BrokerClient client;
  private ControlledActorClock clock;

  @Before
  public void setUp() {
    final GatewayCfg configuration = new GatewayCfg();
    configuration
        .getCluster()
        .setContactPoint(broker.getSocketAddress().toString())
        .setRequestTimeout("3s");
    clock = new ControlledActorClock();

    client = new BrokerClientImpl(configuration, clock);
  }

  @After
  public void tearDown() {
    client.close();
  }

  @Test
  public void shouldSendInitialTopologyRequest() {
    // when
    waitUntil(() -> broker.getReceivedControlMessageRequests().size() == 1);

    // then
    assertTopologyRefreshRequests(1);
  }

  @Test
  public void shouldRefreshTopologyWhenLeaderIsNotKnown() {
    // given
    // initial topology has been fetched
    waitUntil(() -> broker.getReceivedControlMessageRequests().size() == 1);

    broker.jobs().registerCompleteCommand();

    // extend topology
    broker.addPartition(2);

    final long key = (2L << Protocol.KEY_BITS) + 123;
    final BrokerCompleteJobRequest request = new BrokerCompleteJobRequest(key, EMPTY_PAYLOAD);

    // when
    final BrokerResponse<JobRecord> response = client.sendRequest(request).join();

    // then the client has refreshed its topology
    assertThat(response.isResponse()).isTrue();

    assertTopologyRefreshRequests(2);
  }

  @Test
  public void shouldReturnErrorOnRequestFailure() {
    // given
    broker
        .onExecuteCommandRequest(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
        .respondWithError()
        .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
        .errorData("test")
        .register();

    final BrokerResponse<WorkflowInstanceRecord> response =
        client.sendRequest(new BrokerCreateWorkflowInstanceRequest()).join();

    assertThat(response.isError()).isTrue();
    final BrokerError error = response.getError();
    assertThat(error.getCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
    assertThat(error.getMessage()).isEqualTo("test");

    // then
    assertAtLeastTopologyRefreshRequests(1);

    final List<ExecuteCommandRequest> receivedCommandRequests = broker.getReceivedCommandRequests();
    assertThat(receivedCommandRequests).hasSize(1);

    receivedCommandRequests.forEach(
        request -> {
          assertThat(request.valueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE);
          assertThat(request.intent()).isEqualTo(WorkflowInstanceIntent.CREATE);
        });
  }

  @Test
  public void shouldReturnErrorOnReadResponseFailure() {
    // given
    registerCreateWfCommand();

    final BrokerCreateWorkflowInstanceRequest request =
        new BrokerCreateWorkflowInstanceRequest() {
          @Override
          protected WorkflowInstanceRecord toResponseDto(DirectBuffer buffer) {
            throw new RuntimeException("Catch Me");
          }
        };

    // then
    exception.expect(ExecutionException.class);
    exception.expectMessage("Failed to read response: Catch Me");

    // when
    client.sendRequest(request).join();
  }

  @Test
  public void shouldThrowExceptionIfPartitionNotFoundResponse() {
    // given
    broker
        .onExecuteCommandRequest(ValueType.WORKFLOW_INSTANCE, JobIntent.CREATE)
        .respondWithError()
        .errorCode(ErrorCode.PARTITION_NOT_FOUND)
        .errorData("")
        .register();

    // then
    exception.expect(ExecutionException.class);
    exception.expectMessage(containsString("Request timed out after PT3S"));
    // when the partition is repeatedly not found, the client loops
    // over refreshing the topology and making a request that fails and so on. The timeout
    // kicks in at any point in that loop, so we cannot assert the exact error message any more
    // specifically.

    // when
    client.sendRequest(new BrokerCreateWorkflowInstanceRequest()).join();
  }

  @Test
  public void shouldEstablishNewConnectionsAfterDisconnect() {
    // given
    final ClientTransport clientTransport = ((BrokerClientImpl) client).getTransport();

    // ensuring an open connection
    client.getTopologyManager().requestTopology().join();

    final LoggingChannelListener channelListener = new LoggingChannelListener();
    clientTransport.registerChannelListener(channelListener).join();

    // when
    broker.closeTransport();
    System.out.println("Broker transport closed");
    broker.bindTransport();
    System.out.println("Broker transport bound");

    // then
    waitUntil(
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
    broker.addPartition(0);
    broker.addPartition(1);

    stubJobResponse();

    // when then
    for (int i = 0; i < clientMaxRequests; i++) {
      client.sendRequest(new BrokerCreateWorkflowInstanceRequest()).join();
    }
  }

  @Test
  public void shouldReleaseRequestsOnGet() {
    // given

    broker.clearTopology();
    broker.addPartition(0);
    broker.addPartition(1);

    stubJobResponse();

    final List<ActorFuture<BrokerResponse<WorkflowInstanceRecord>>> futures = new ArrayList<>();
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<BrokerResponse<WorkflowInstanceRecord>> future =
          client.sendRequest(new BrokerCreateWorkflowInstanceRequest());

      futures.add(future);
    }

    // when
    for (final ActorFuture<BrokerResponse<WorkflowInstanceRecord>> future : futures) {
      future.join();
    }

    // then
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<BrokerResponse<WorkflowInstanceRecord>> future =
          client.sendRequest(new BrokerCreateWorkflowInstanceRequest());

      futures.add(future);
    }
  }

  @Test
  public void shouldReleaseRequestsOnTimeout() {
    // given
    broker.onExecuteCommandRequest(ValueType.JOB, JobIntent.COMPLETE).doNotRespond();

    // given
    final List<ActorFuture<BrokerResponse<WorkflowInstanceRecord>>> futures = new ArrayList<>();
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<BrokerResponse<WorkflowInstanceRecord>> future =
          client.sendRequest(new BrokerCreateWorkflowInstanceRequest());
      futures.add(future);
    }

    // when
    for (final ActorFuture<BrokerResponse<WorkflowInstanceRecord>> future : futures) {
      try {
        future.join();
        fail("exception expected");
      } catch (final Exception e) {
        // expected
      }
    }

    // then
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<BrokerResponse<WorkflowInstanceRecord>> future =
          client.sendRequest(new BrokerCreateWorkflowInstanceRequest());

      futures.add(future);
    }
  }

  @Test
  public void shouldThrowExceptionOnTimeout() {
    // given
    broker.onExecuteCommandRequest(ValueType.JOB, JobIntent.COMPLETE).doNotRespond();

    // then
    exception.expect(ExecutionException.class);
    exception.expectMessage("Request timed out after PT3S");

    // when
    client.sendRequest(new BrokerCompleteJobRequest(1, EMPTY_PAYLOAD)).join();
  }

  @Test
  public void shouldIncludeCallingFrameInExceptionStacktraceOnAsyncRootCause() {
    // given
    broker.jobs().registerCompleteCommand(ExecuteCommandResponseBuilder::rejection);

    // when
    try {
      client.sendRequest(new BrokerCreateWorkflowInstanceRequest()).join();

      fail("should throw exception");
    } catch (final Exception e) {
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
  public void shouldThrottleTopologyRefreshRequestsWhenPartitionCannotBeDetermined() {
    // when
    final long start = System.currentTimeMillis();
    assertThatThrownBy(() -> client.sendRequest(new BrokerCreateWorkflowInstanceRequest()).join());
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
        (requestDuration / BrokerTopologyManagerImpl.MIN_REFRESH_INTERVAL_MILLIS.toMillis()) + 4;

    assertThat(actualTopologyRequests).isLessThanOrEqualTo(expectedMaximumTopologyRequests);
  }

  @Test
  public void shouldThrottleTopologyRefreshRequestsWhenPartitionLeaderCannotBeDetermined() {
    // given
    final int nonExistingPartition = 999;
    final long key = ((long) nonExistingPartition << Protocol.KEY_BITS) + 123;

    // when
    final long start = System.currentTimeMillis();
    assertThatThrownBy(
        () -> client.sendRequest(new BrokerCompleteJobRequest(key, EMPTY_PAYLOAD)).join());
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
        (requestDuration / BrokerTopologyManagerImpl.MIN_REFRESH_INTERVAL_MILLIS.toMillis()) + 4;

    assertThat(actualTopologyRequests).isLessThanOrEqualTo(expectedMaximumTopologyRequests);
  }

  // TODO: revise the tests below

  @Test
  public void shouldReturnRejectionWithCorrectTypeAndReason() {
    // given
    broker.jobs().registerCompleteCommand(b -> b.rejection(RejectionType.INVALID_ARGUMENT, "foo"));

    // when
    final BrokerResponse<JobRecord> response =
        client.sendRequest(new BrokerCompleteJobRequest(79, EMPTY_PAYLOAD)).join();

    // then
    assertThat(response.isRejection()).isTrue();
    final BrokerRejection rejection = response.getRejection();
    assertThat(rejection.getType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getReason()).isEqualTo("foo");
  }

  @Test
  public void shouldFailRequestIfTopologyCannotBeRefreshed() {
    // given
    broker.onTopologyRequest().doNotRespond();
    broker.onExecuteCommandRequest(ValueType.JOB, JobIntent.COMPLETE).doNotRespond();

    // then
    exception.expect(ExecutionException.class);
    exception.expectMessage("Request timed out after PT1S");

    // when
    client.sendRequest(new BrokerCompleteJobRequest(0, EMPTY_PAYLOAD)).join();
  }

  @Test
  public void shouldRetryTopologyRequestAfterTimeout() {
    // given
    final int topologyTimeoutSeconds = 1;

    broker.onTopologyRequest().doNotRespond();
    broker.jobs().registerCompleteCommand();

    // wait for a hanging topology request
    waitUntil(
        () ->
            broker
                    .getReceivedControlMessageRequests()
                    .stream()
                    .filter(r -> r.messageType() == ControlMessageType.REQUEST_TOPOLOGY)
                    .count()
                == 1);

    broker.stubTopologyRequest(); // make topology available
    clock.addTime(Duration.ofSeconds(topologyTimeoutSeconds + 1)); // let request time out

    // when making a new request
    // then the topology has been refreshed and the request succeeded
    client.sendRequest(new BrokerCompleteJobRequest(0, EMPTY_PAYLOAD)).join();
  }

  private void stubJobResponse() {
    registerCreateWfCommand();
    broker.jobs().registerCompleteCommand();
  }

  protected enum ConnectionState {
    CONNECTED,
    CLOSED
  }

  protected static class LoggingChannelListener implements TransportListener {

    List<ConnectionState> connectionState = new CopyOnWriteArrayList<>();

    @Override
    public void onConnectionEstablished(final RemoteAddress remoteAddress) {
      connectionState.add(ConnectionState.CONNECTED);
    }

    @Override
    public void onConnectionClosed(final RemoteAddress remoteAddress) {
      connectionState.add(ConnectionState.CLOSED);
    }
  }

  private void assertTopologyRefreshRequests(final int count) {
    final List<ControlMessageRequest> receivedControlMessageRequests =
        broker.getReceivedControlMessageRequests();
    assertThat(receivedControlMessageRequests).hasSize(count);

    receivedControlMessageRequests.forEach(
        request -> {
          assertThat(request.messageType()).isEqualTo(REQUEST_TOPOLOGY);
          assertThat(request.getData()).isNull();
        });
  }

  private void assertAtLeastTopologyRefreshRequests(final int count) {
    final List<ControlMessageRequest> receivedControlMessageRequests =
        broker.getReceivedControlMessageRequests();
    assertThat(receivedControlMessageRequests.size()).isGreaterThanOrEqualTo(count);

    receivedControlMessageRequests.forEach(
        request -> {
          assertThat(request.messageType()).isEqualTo(REQUEST_TOPOLOGY);
          assertThat(request.getData()).isNull();
        });
  }

  public void registerCreateWfCommand() {
    final ExecuteCommandResponseBuilder builder =
        broker
            .onExecuteCommandRequest(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CREATE)
            .respondWith()
            .event()
            .intent(WorkflowInstanceIntent.ELEMENT_READY)
            .key(r -> r.key())
            .value()
            .allOf((r) -> r.getCommand())
            .done();

    builder.register();
  }
}
