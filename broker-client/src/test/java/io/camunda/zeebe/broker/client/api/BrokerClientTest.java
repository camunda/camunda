/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.client.api;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.camunda.zeebe.test.broker.protocol.brokerapi.StubBroker;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.agrona.DirectBuffer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public final class BrokerClientTest {

  @AutoCloseResource
  private final ActorScheduler actorScheduler = ActorScheduler.newActorScheduler().build();

  @AutoCloseResource private final StubBroker broker = new StubBroker().start();

  @AutoCloseResource private BrokerClient client;

  @AutoCloseResource private AtomixCluster atomixCluster;

  // keep as field to ensure it gets closed at the end
  @SuppressWarnings({"FieldCanBeLocal", "Unused"})
  @AutoCloseResource
  private BrokerTopologyManager topologyManager;

  @BeforeEach
  void beforeEach() {
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
    actorScheduler.start();

    final var topologyManager =
        new BrokerTopologyManagerImpl(() -> atomixCluster.getMembershipService().getMembers());
    this.topologyManager = topologyManager;
    actorScheduler.submitActor(topologyManager).join();
    atomixCluster.getMembershipService().addListener(topologyManager);

    topologyManager.updateTopology(
        topology -> {
          topology.addPartitionIfAbsent(START_PARTITION_ID);
          topology.setPartitionLeader(START_PARTITION_ID, 0, 1);
          topology.addBrokerIfAbsent(0);
          topology.setBrokerAddressIfPresent(0, stubAddress.toString());
        });
    Awaitility.await("Topology is updated")
        .untilAsserted(
            () -> assertThat(topologyManager.getTopology().getPartitions()).isNotEmpty());

    client =
        new BrokerClientImpl(
            Duration.ofSeconds(5),
            atomixCluster.getMessagingService(),
            atomixCluster.getEventService(),
            actorScheduler,
            topologyManager);

    client.start().forEach(ActorFuture::join);
  }

  @Test
  void shouldReturnErrorOnRequestFailure() {
    // given
    broker
        .onExecuteCommandRequest(TestCommand.VALUE_TYPE, TestCommand.INTENT)
        .respondWithError()
        .errorCode(ErrorCode.INTERNAL_ERROR)
        .errorData("test")
        .register();

    assertThatThrownBy(() -> client.sendRequestWithRetry(new TestCommand()).join())
        .hasCauseInstanceOf(BrokerErrorException.class)
        .hasCause(new BrokerErrorException(new BrokerError(ErrorCode.INTERNAL_ERROR, "test")));

    // then
    final var receivedCommandRequests = broker.getReceivedCommandRequests();
    assertThat(receivedCommandRequests).hasSize(1);

    receivedCommandRequests.forEach(
        request -> {
          assertThat(request.valueType()).isEqualTo(TestCommand.VALUE_TYPE);
          assertThat(request.intent()).isEqualTo(TestCommand.INTENT);
        });
  }

  @Test
  void shouldReturnErrorOnReadResponseFailure() {
    // given
    broker
        .onExecuteCommandRequest(TestCommand.VALUE_TYPE, TestCommand.INTENT)
        .respondWith()
        .event()
        .intent(TestCommand.INTENT)
        .key(ExecuteCommandRequest::key)
        .value()
        .allOf(ExecuteCommandRequest::getCommand)
        .done()
        .register();

    final var request =
        new TestCommand() {
          @Override
          protected UnifiedRecordValue toResponseDto(final DirectBuffer buffer) {
            throw new RuntimeException("Catch Me");
          }
        };

    // then
    assertThatThrownBy(() -> client.sendRequestWithRetry(request).join())
        .hasCauseInstanceOf(BrokerResponseException.class)
        .hasMessageContaining("Catch Me");
  }

  @Test
  void shouldTimeoutIfPartitionLeaderMismatchResponse() {
    // given
    broker
        .onExecuteCommandRequest(TestCommand.VALUE_TYPE, TestCommand.INTENT)
        .respondWithError()
        .errorCode(ErrorCode.PARTITION_LEADER_MISMATCH)
        .errorData("")
        .register();

    // when
    final var future = client.sendRequestWithRetry(new TestCommand());

    // then
    // when the partition is repeatedly not found, the client loops
    // over refreshing the topology and making a request that fails and so on. The timeout
    // kicks in at any point in that loop, so we cannot assert the exact error message anymore
    // specifically. It is also possible that Atomix times out beforehand if we calculated a very
    // small timeout for the request, e.g. < 50ms, so we also cannot assert the value of the
    // timeout
    assertThatThrownBy(future::join).hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  void shouldNotTimeoutIfPartitionLeaderMismatchResponseWhenRetryDisabled() {
    // given
    broker
        .onExecuteCommandRequest(TestCommand.VALUE_TYPE, TestCommand.INTENT)
        .respondWithError()
        .errorCode(ErrorCode.PARTITION_LEADER_MISMATCH)
        .errorData("")
        .register();

    // when
    final var future = client.sendRequest(new TestCommand());

    // then
    assertThatThrownBy(future::join)
        .hasCause(
            new BrokerErrorException(new BrokerError(ErrorCode.PARTITION_LEADER_MISMATCH, "")));
  }

  @Test
  void shouldCloseIdempotently() {
    // given
    client.close();

    // when - then
    assertThatCode(() -> client.close()).doesNotThrowAnyException();
  }

  @Test
  void shouldThrowExceptionOnTimeout() {
    // given
    broker.onExecuteCommandRequest(TestCommand.VALUE_TYPE, TestCommand.INTENT).doNotRespond();

    // when
    final var request = new TestCommand();
    request.setPartitionId(1);
    final var async = client.sendRequestWithRetry(request, Duration.ofMillis(100));

    // then
    assertThatThrownBy(async::join).hasRootCauseInstanceOf(TimeoutException.class);
  }

  @Test
  void shouldThrowExceptionWhenPartitionNotFound() {
    // given
    final var request = new TestCommand();
    request.setPartitionId(0);

    // when
    final var async = client.sendRequestWithRetry(request);

    // then
    final var expected =
        "Expected to execute command on partition 0, but either it does not exist, or the gateway is not yet aware of it";
    assertThatThrownBy(async::join)
        .hasCauseInstanceOf(PartitionNotFoundException.class)
        .hasMessageContaining(expected);
  }

  @Test
  void shouldIncludeCallingFrameInExceptionStacktraceOnAsyncRootCause(final TestInfo testInfo) {
    // given
    broker
        .onExecuteCommandRequest(TestCommand.VALUE_TYPE, TestCommand.INTENT)
        .respondWith()
        .rejection();

    // when
    try {
      client.sendRequestWithRetry(new TestCommand()).join();
      fail("should throw exception");
    } catch (final Exception e) {
      // then
      assertThat(e.getStackTrace())
          .anySatisfy(
              frame -> {
                assertThat(frame.getClassName()).isEqualTo(getClass().getName());
                assertThat(frame.getMethodName())
                    .isEqualTo(testInfo.getTestMethod().orElseThrow().getName());
              });
    }
  }

  @Test
  void shouldReturnRejectionWithCorrectTypeAndReason() {
    // given
    broker
        .onExecuteCommandRequest(TestCommand.VALUE_TYPE, TestCommand.INTENT)
        .respondWith()
        .event()
        .intent(TestCommand.INTENT)
        .key(1L)
        .rejection(RejectionType.INVALID_ARGUMENT, "foo")
        .value()
        .allOf(ExecuteCommandRequest::getCommand)
        .done()
        .register();

    // when
    final var responseFuture = client.sendRequestWithRetry(new TestCommand(1L));

    // then
    assertThatThrownBy(responseFuture::join)
        .hasCauseInstanceOf(BrokerRejectionException.class)
        .hasCause(
            new BrokerRejectionException(
                new BrokerRejection(TestCommand.INTENT, 1, RejectionType.INVALID_ARGUMENT, "foo")));
  }

  private static class TestCommand extends BrokerExecuteCommand<UnifiedRecordValue> {
    private static final ValueType VALUE_TYPE = ValueType.JOB;
    private static final Intent INTENT = JobIntent.YIELD;

    private final UnifiedRecordValue record;
    private final long key;

    private TestCommand() {
      this(new UnifiedRecordValue(10));
    }

    private TestCommand(final long key) {
      this(new UnifiedRecordValue(10), key);
    }

    private TestCommand(final UnifiedRecordValue record) {
      this(record, Protocol.encodePartitionId(1, 1));
    }

    private TestCommand(final UnifiedRecordValue record, final long key) {
      super(VALUE_TYPE, INTENT);
      this.record = record;
      this.key = key;
    }

    @Override
    public long getKey() {
      return key;
    }

    @Override
    public BufferWriter getRequestWriter() {
      return record;
    }

    @Override
    protected UnifiedRecordValue toResponseDto(final DirectBuffer buffer) {
      final var response = new UnifiedRecordValue(10);
      response.wrap(buffer);
      return response;
    }
  }
}
