/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.broker;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.CoreMatchers.containsString;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.utils.net.Address;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterStateImpl;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.response.BrokerError;
import io.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import io.zeebe.msgpack.value.DocumentValue;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandResponseBuilder;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.socket.SocketUtil;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

public final class BrokerClientTest {

  @Rule public final StubBrokerRule broker = new StubBrokerRule();
  @Rule public AutoCloseableRule closeables = new AutoCloseableRule();
  @Rule public final ExpectedException exception = ExpectedException.none();
  @Rule public final TestName testContext = new TestName();
  private BrokerClient client;
  private AtomixCluster atomixCluster;

  @Before
  public void setUp() {
    final GatewayCfg configuration = new GatewayCfg();
    configuration
        .getCluster()
        .setHost("0.0.0.0")
        .setPort(SocketUtil.getNextAddress().getPort())
        .setContactPoint(broker.getSocketAddress().toString())
        .setRequestTimeout(Duration.ofSeconds(3));
    configuration.init();

    final ControlledActorClock clock = new ControlledActorClock();

    final var stubAddress = Address.from(broker.getCurrentStubHost(), broker.getCurrentStubPort());
    final var stubNode = Node.builder().withAddress(stubAddress).build();
    final var listOfNodes = List.of(stubNode);
    atomixCluster =
        AtomixCluster.builder()
            .withPort(SocketUtil.getNextAddress().getPort())
            .withMemberId("gateway")
            .withClusterId("cluster")
            .withMembershipProvider(
                BootstrapDiscoveryProvider.builder().withNodes(listOfNodes).build())
            .build();
    atomixCluster.start().join();

    client = new BrokerClientImpl(configuration, atomixCluster, clock);

    final BrokerClusterStateImpl topology = new BrokerClusterStateImpl();
    topology.addPartitionIfAbsent(START_PARTITION_ID);
    topology.setPartitionLeader(START_PARTITION_ID, 0, 1);
    topology.addBrokerIfAbsent(0);
    topology.setBrokerAddressIfPresent(0, stubAddress.toString());

    ((BrokerTopologyManagerImpl) client.getTopologyManager()).setTopology(topology);
  }

  @After
  public void tearDown() {
    client.close();
    atomixCluster.stop().join();
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
          protected WorkflowInstanceCreationRecord toResponseDto(final DirectBuffer buffer) {
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
        .onExecuteCommandRequest(
            ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationIntent.CREATE)
        .respondWithError()
        .errorCode(ErrorCode.PARTITION_LEADER_MISMATCH)
        .errorData("")
        .register();

    // then
    exception.expect(ExecutionException.class);
    exception.expectMessage(containsString("timed out"));
    // when the partition is repeatedly not found, the client loops
    // over refreshing the topology and making a request that fails and so on. The timeout
    // kicks in at any point in that loop, so we cannot assert the exact error message any more
    // specifically. It is also possible that Atomix times out before hand if we calculated a very
    // small time out for the request, e.g. < 50ms, so we also cannot assert the value of the
    // timeout

    // when
    client.sendRequest(new BrokerCreateWorkflowInstanceRequest()).join();
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
      client.sendRequest(new BrokerCompleteJobRequest(1, new UnsafeBuffer(new byte[0]))).join();

      fail("should throw exception");
    } catch (final Exception e) {
      // then
      assertThat(e.getStackTrace())
          .anySatisfy(
              frame -> {
                assertThat(frame.getClassName()).isEqualTo(getClass().getName());
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

  private void registerCreateWfCommand() {
    final ExecuteCommandResponseBuilder builder =
        broker
            .onExecuteCommandRequest(
                ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationIntent.CREATE)
            .respondWith()
            .event()
            .intent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .key(ExecuteCommandRequest::key)
            .value()
            .allOf(ExecuteCommandRequest::getCommand)
            .done();

    builder.register();
  }
}
