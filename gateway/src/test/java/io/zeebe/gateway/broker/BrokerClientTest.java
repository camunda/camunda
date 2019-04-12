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

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterMembershipService;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterStateImpl;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.request.BrokerDeployWorkflowRequest;
import io.zeebe.gateway.impl.broker.response.BrokerError;
import io.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.msgpack.value.DocumentValue;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandResponseBuilder;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.TransportListener;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import org.agrona.DirectBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

public class BrokerClientTest {

  @Rule public StubBrokerRule broker = new StubBrokerRule();
  @Rule public AutoCloseableRule closeables = new AutoCloseableRule();
  @Rule public ExpectedException exception = ExpectedException.none();
  @Rule public TestName testContext = new TestName();
  private BrokerClient client;
  private ControlledActorClock clock;
  private static int clientMaxRequests = 128;

  @Before
  public void setUp() {
    final GatewayCfg configuration = new GatewayCfg();
    configuration
        .getCluster()
        .setHost("0.0.0.0")
        .setPort(SocketUtil.getNextAddress().port())
        .setContactPoint(broker.getSocketAddress().toString())
        .setRequestTimeout("3s");
    clock = new ControlledActorClock();

    final AtomixCluster atomixCluster = mock(AtomixCluster.class);
    final ClusterMembershipService memberShipService = mock(ClusterMembershipService.class);
    when(atomixCluster.getMembershipService()).thenReturn(memberShipService);

    client = new BrokerClientImpl(configuration, atomixCluster, clock);

    ((BrokerClientImpl) client).getTransport().registerEndpoint(0, broker.getSocketAddress());

    final BrokerClusterStateImpl topology = new BrokerClusterStateImpl();
    topology.addPartitionIfAbsent(START_PARTITION_ID);
    topology.setPartitionLeader(START_PARTITION_ID, 0);

    ((BrokerTopologyManagerImpl) client.getTopologyManager()).setTopology(topology);
  }

  @After
  public void tearDown() {
    client.close();
  }

  @Test
  public void shouldReturnErrorOnRequestFailure() {
    // given
    broker
        .onExecuteCommandRequest(
            ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationIntent.CREATE)
        .respondWithError()
        .errorCode(ErrorCode.INTERNAL_ERROR)
        .errorData("test")
        .register();

    final BrokerResponse<WorkflowInstanceCreationRecord> response =
        client.sendRequest(new BrokerCreateWorkflowInstanceRequest()).join();

    assertThat(response.isError()).isTrue();
    final BrokerError error = response.getError();
    assertThat(error.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    assertThat(error.getMessage()).isEqualTo("test");

    // then
    final List<ExecuteCommandRequest> receivedCommandRequests = broker.getReceivedCommandRequests();
    assertThat(receivedCommandRequests).hasSize(1);

    receivedCommandRequests.forEach(
        request -> {
          assertThat(request.valueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE_CREATION);
          assertThat(request.intent()).isEqualTo(WorkflowInstanceCreationIntent.CREATE);
        });
  }

  @Test
  public void shouldReturnErrorOnReadResponseFailure() {
    // given
    registerCreateWfCommand();

    final BrokerCreateWorkflowInstanceRequest request =
        new BrokerCreateWorkflowInstanceRequest() {
          @Override
          protected WorkflowInstanceCreationRecord toResponseDto(DirectBuffer buffer) {
            throw new RuntimeException("Catch Me");
          }
        };

    // then
    exception.expect(ExecutionException.class);
    exception.expectMessage("Catch Me");

    // when
    client.sendRequest(request).join();
  }

  @Test
  public void shouldThrowExceptionIfPartitionNotFoundResponse() {
    // given
    broker
        .onExecuteCommandRequest(ValueType.WORKFLOW_INSTANCE, JobIntent.CREATE)
        .respondWithError()
        .errorCode(ErrorCode.PARTITION_LEADER_MISMATCH)
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
    final LoggingChannelListener channelListener = new LoggingChannelListener();
    clientTransport.registerChannelListener(channelListener).join();

    // ensuring an open connection
    broker.onExecuteCommandRequest(ValueType.DEPLOYMENT, DeploymentIntent.CREATE).doNotRespond();
    client.sendRequest(new BrokerDeployWorkflowRequest());

    // when
    broker.closeTransport();
    broker.bindTransport();

    // then
    waitUntil(
        () ->
            channelListener.connectionState.stream()
                    .filter(state -> state == ConnectionState.CONNECTED)
                    .count()
                == 2); // listener invocation is asynchronous

    assertThat(channelListener.connectionState).last().isSameAs(ConnectionState.CONNECTED);
  }

  @Test
  public void shouldRetryTopologyRequestAfterTimeout() {
    // given
    broker
        .onExecuteCommandRequest(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
        .respondWithError()
        .errorCode(ErrorCode.PARTITION_LEADER_MISMATCH)
        .errorData("")
        .register();

    // when
    client.sendRequest(new BrokerDeployWorkflowRequest());

    // then
    waitUntil(() -> broker.getAllReceivedRequests().size() > 1, 100);
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

    final List<ActorFuture<BrokerResponse<WorkflowInstanceCreationRecord>>> futures =
        new ArrayList<>();
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<BrokerResponse<WorkflowInstanceCreationRecord>> future =
          client.sendRequest(new BrokerCreateWorkflowInstanceRequest());

      futures.add(future);
    }

    // when
    for (final ActorFuture<BrokerResponse<WorkflowInstanceCreationRecord>> future : futures) {
      future.join();
    }

    // then
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<BrokerResponse<WorkflowInstanceCreationRecord>> future =
          client.sendRequest(new BrokerCreateWorkflowInstanceRequest());

      futures.add(future);
    }
  }

  @Test
  public void shouldReleaseRequestsOnTimeout() {
    // given
    broker.onExecuteCommandRequest(ValueType.JOB, JobIntent.COMPLETE).doNotRespond();

    // given
    final List<ActorFuture<BrokerResponse<WorkflowInstanceCreationRecord>>> futures =
        new ArrayList<>();
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<BrokerResponse<WorkflowInstanceCreationRecord>> future =
          client.sendRequest(new BrokerCreateWorkflowInstanceRequest());
      futures.add(future);
    }

    // when
    for (final ActorFuture<BrokerResponse<WorkflowInstanceCreationRecord>> future : futures) {
      try {
        future.join();
        fail("exception expected");
      } catch (final Exception e) {
        // expected
      }
    }

    // then
    for (int i = 0; i < clientMaxRequests; i++) {
      final ActorFuture<BrokerResponse<WorkflowInstanceCreationRecord>> future =
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
    client.sendRequest(new BrokerCompleteJobRequest(1, DocumentValue.EMPTY_DOCUMENT)).join();
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
  public void shouldReturnRejectionWithCorrectTypeAndReason() {
    // given
    broker.jobs().registerCompleteCommand(b -> b.rejection(RejectionType.INVALID_ARGUMENT, "foo"));

    // when
    final BrokerResponse<JobRecord> response =
        client
            .sendRequest(
                new BrokerCompleteJobRequest(
                    Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 79),
                    DocumentValue.EMPTY_DOCUMENT))
            .join();

    // then
    assertThat(response.isRejection()).isTrue();
    final BrokerRejection rejection = response.getRejection();
    assertThat(rejection.getType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getReason()).isEqualTo("foo");
  }

  private void stubJobResponse() {
    registerCreateWfCommand();
    broker.jobs().registerCompleteCommand();
  }

  public void registerCreateWfCommand() {
    final ExecuteCommandResponseBuilder builder =
        broker
            .onExecuteCommandRequest(
                ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationIntent.CREATE)
            .respondWith()
            .event()
            .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .key(r -> r.key())
            .value()
            .allOf((r) -> r.getCommand())
            .done();

    builder.register();
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
}
