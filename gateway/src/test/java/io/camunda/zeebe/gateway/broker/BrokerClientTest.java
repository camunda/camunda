/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.broker;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.gateway.cmd.BrokerErrorException;
import io.camunda.zeebe.gateway.cmd.BrokerRejectionException;
import io.camunda.zeebe.gateway.cmd.ClientResponseException;
import io.camunda.zeebe.gateway.cmd.PartitionNotFoundException;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerClusterStateImpl;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManagerImpl;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerError;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.camunda.zeebe.test.broker.protocol.brokerapi.ExecuteCommandResponseBuilder;
import io.camunda.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.netty.util.NetUtil;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public final class BrokerClientTest {

  @Rule public final ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
  @Rule public final StubBrokerRule broker = new StubBrokerRule();

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
        .setInitialContactPoints(
            Collections.singletonList(NetUtil.toSocketAddressString(broker.getSocketAddress())))
        .setRequestTimeout(Duration.ofSeconds(3));
    configuration.init();

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

    client =
        new BrokerClientImpl(
            configuration.getCluster().getRequestTimeout(),
            atomixCluster.getMessagingService(),
            atomixCluster.getMembershipService(),
            atomixCluster.getEventService(),
            actorScheduler.get());

    client.start().forEach(ActorFuture::join);

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
            ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATE)
        .respondWithError()
        .errorCode(ErrorCode.INTERNAL_ERROR)
        .errorData("test")
        .register();

    assertThatThrownBy(
            () -> client.sendRequestWithRetry(new BrokerCreateProcessInstanceRequest()).join())
        .hasCauseInstanceOf(BrokerErrorException.class)
        .hasCause(new BrokerErrorException(new BrokerError(ErrorCode.INTERNAL_ERROR, "test")));

    // then
    final List<ExecuteCommandRequest> receivedCommandRequests = broker.getReceivedCommandRequests();
    assertThat(receivedCommandRequests).hasSize(1);

    receivedCommandRequests.forEach(
        request -> {
          assertThat(request.valueType()).isEqualTo(ValueType.PROCESS_INSTANCE_CREATION);
          assertThat(request.intent()).isEqualTo(ProcessInstanceCreationIntent.CREATE);
        });
  }

  @Test
  public void shouldReturnErrorOnReadResponseFailure() {
    // given
    registerCreateWfCommand();

    final BrokerCreateProcessInstanceRequest request =
        new BrokerCreateProcessInstanceRequest() {
          @Override
          protected ProcessInstanceCreationRecord toResponseDto(final DirectBuffer buffer) {
            throw new RuntimeException("Catch Me");
          }
        };

    // then
    assertThatThrownBy(() -> client.sendRequestWithRetry(request).join())
        .hasCauseInstanceOf(ClientResponseException.class)
        .hasMessageContaining("Catch Me");
  }

  @Test
  public void shouldTimeoutIfPartitionLeaderMismatchResponse() {
    // given
    broker
        .onExecuteCommandRequest(
            ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATE)
        .respondWithError()
        .errorCode(ErrorCode.PARTITION_LEADER_MISMATCH)
        .errorData("")
        .register();

    // when
    final var future = client.sendRequestWithRetry(new BrokerCreateProcessInstanceRequest());

    // then
    // when the partition is repeatedly not found, the client loops
    // over refreshing the topology and making a request that fails and so on. The timeout
    // kicks in at any point in that loop, so we cannot assert the exact error message any more
    // specifically. It is also possible that Atomix times out before hand if we calculated a very
    // small time out for the request, e.g. < 50ms, so we also cannot assert the value of the
    // timeout
    assertThatThrownBy(future::join).hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  public void shouldNotTimeoutIfPartitionLeaderMismatchResponseWhenRetryDisabled() {
    // given
    broker
        .onExecuteCommandRequest(
            ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATE)
        .respondWithError()
        .errorCode(ErrorCode.PARTITION_LEADER_MISMATCH)
        .errorData("")
        .register();

    // when
    final var future = client.sendRequest(new BrokerCreateProcessInstanceRequest());

    // then
    assertThatThrownBy(future::join)
        .hasCause(
            new BrokerErrorException(new BrokerError(ErrorCode.PARTITION_LEADER_MISMATCH, "")));
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

    // when
    final long key = Protocol.encodePartitionId(1, 123);
    final var request = new BrokerCompleteJobRequest(key, DocumentValue.EMPTY_DOCUMENT);
    request.setPartitionId(1);
    final var async = client.sendRequestWithRetry(request, Duration.ofMillis(100));

    // then
    assertThatThrownBy(async::join).hasRootCauseInstanceOf(TimeoutException.class);
  }

  @Test
  public void shouldThrowExceptionWhenPartitionNotFound() {
    // given
    final var request = new BrokerSetVariablesRequest();
    request.setElementInstanceKey(0);
    request.setPartitionId(0);
    request.setLocal(false);

    // when
    final var async = client.sendRequestWithRetry(request);

    // then
    final String expected =
        "Expected to execute command on partition 0, but either it does not exist, or the gateway is not yet aware of it";
    assertThatThrownBy(async::join)
        .hasCauseInstanceOf(PartitionNotFoundException.class)
        .hasMessageContaining(expected);
  }

  @Test
  public void shouldIncludeCallingFrameInExceptionStacktraceOnAsyncRootCause() {
    // given
    broker.jobs().registerCompleteCommand(ExecuteCommandResponseBuilder::rejection);

    // when
    try {
      client
          .sendRequestWithRetry(new BrokerCompleteJobRequest(1, new UnsafeBuffer(new byte[0])))
          .join();

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
    final var responseFuture =
        client.sendRequestWithRetry(
            new BrokerCompleteJobRequest(
                Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 79),
                DocumentValue.EMPTY_DOCUMENT));

    // then
    assertThatThrownBy(responseFuture::join)
        .hasCauseInstanceOf(BrokerRejectionException.class)
        .hasCause(
            new BrokerRejectionException(
                new BrokerRejection(
                    JobIntent.COMPLETE,
                    Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 79),
                    RejectionType.INVALID_ARGUMENT,
                    "foo")));
  }

  private void registerCreateWfCommand() {
    final ExecuteCommandResponseBuilder builder =
        broker
            .onExecuteCommandRequest(
                ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATE)
            .respondWith()
            .event()
            .intent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .key(ExecuteCommandRequest::key)
            .value()
            .allOf(ExecuteCommandRequest::getCommand)
            .done();

    builder.register();
  }
}
