/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.fail;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
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
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public final class BrokerClientTest {

  private final ActorScheduler actorScheduler = ActorScheduler.newActorScheduler().build();

  private final StubBroker broker = new StubBroker().start();
  private BrokerClient client;
  private AtomixCluster atomixCluster;
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  // keep as field to ensure it gets closed at the end
  @SuppressWarnings({"FieldCanBeLocal", "Unused"})
  private BrokerTopologyManagerImpl topologyManager;

  @AfterEach
  void afterEach() {
    // We need to make sure that the Actor is closed last, otherwise we end up in a deadlock
    // Thus, usage of @AutoClose is not possible, as there are no guarantees about ordering
    CloseHelper.quietCloseAll(topologyManager, atomixCluster, broker, client, actorScheduler);
  }

  @BeforeEach
  void beforeEach() {
    final var meterRegistry = new SimpleMeterRegistry();
    final var brokerAddress =
        Address.from(broker.getCurrentStubHost(), broker.getCurrentStubPort());
    final var membership = BootstrapDiscoveryProvider.builder().withNodes(brokerAddress).build();
    atomixCluster =
        AtomixCluster.builder(meterRegistry)
            .withPort(SocketUtil.getNextAddress().getPort())
            .withMembershipProvider(membership)
            .withClusterId(broker.clusterId())
            .build();
    atomixCluster.start().join();
    actorScheduler.start();

    final var topologyManager =
        new BrokerTopologyManagerImpl(
            () -> atomixCluster.getMembershipService().getMembers(),
            new BrokerClientTopologyMetrics(meterRegistry));
    this.topologyManager = topologyManager;
    actorScheduler.submitActor(topologyManager).join();
    atomixCluster.getMembershipService().addListener(topologyManager);

    final var clusterTopology =
        ClusterConfiguration.init()
            .addMember(
                broker.member().id(),
                MemberState.initializeAsActive(Map.of(1, PartitionState.active(1, null))));

    topologyManager.onClusterConfigurationUpdated(clusterTopology);
    topologyManager.event(new ClusterMembershipEvent(Type.MEMBER_ADDED, broker.member()));
    Awaitility.await("Topology is updated")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getLeaderForPartition(1))
                    .isNotEqualTo(BrokerClusterState.NODE_ID_NULL));

    client =
        new BrokerClientImpl(
            Duration.ofSeconds(5),
            atomixCluster.getMessagingService(),
            atomixCluster.getEventService(),
            actorScheduler,
            topologyManager,
            new BrokerClientRequestMetrics(meterRegistry));
    client.start().forEach(ActorFuture::join);
  }

  @Test
  void shouldReturnErrorOnRequestFailure() {
    // given
    registerError(broker, ErrorCode.INTERNAL_ERROR, "test");

    // when
    final Future<?> result = client.sendRequestWithRetry(new TestCommand());

    // then
    final var receivedCommandRequests = broker.getReceivedCommandRequests();
    assertThat(result)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableThat()
        .withCause(new BrokerErrorException(new BrokerError(ErrorCode.INTERNAL_ERROR, "test")));
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
    final var error = new RuntimeException("Catch Me");
    final var request =
        new TestCommand() {
          @Override
          protected UnifiedRecordValue toResponseDto(final DirectBuffer buffer) {
            throw error;
          }
        };
    registerSuccessResponse(broker);

    // when
    final Future<?> response = client.sendRequestWithRetry(request);

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableThat()
        .withCauseInstanceOf(BrokerResponseException.class)
        .havingRootCause()
        .isSameAs(error);
  }

  @Test
  void shouldTimeoutIfPartitionLeaderMismatchResponse() {
    // given
    registerError(broker, ErrorCode.PARTITION_LEADER_MISMATCH, "");

    // when
    final Future<?> response = client.sendRequestWithRetry(new TestCommand());

    // then
    // when the partition is repeatedly not found, the client loops
    // over refreshing the topology and making a request that fails and so on. The timeout
    // kicks in at any point in that loop, so we cannot assert the exact error message anymore
    // specifically. It is also possible that Atomix times out beforehand if we calculated a very
    // small timeout for the request, e.g. < 50ms, so we also cannot assert the value of the
    // timeout
    assertThat(response)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableThat()
        .withCauseInstanceOf(TimeoutException.class);
  }

  @Test
  void shouldNotTimeoutIfPartitionLeaderMismatchResponseWhenRetryDisabled() {
    // given
    registerError(broker, ErrorCode.PARTITION_LEADER_MISMATCH, "");

    // when
    final Future<?> response = client.sendRequest(new TestCommand());

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableThat()
        .withCause(
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
    final Future<?> response = client.sendRequestWithRetry(request, Duration.ofMillis(100));

    // then
    assertThat(response)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableThat()
        .withRootCauseInstanceOf(TimeoutException.class);
  }

  @Test
  void shouldThrowExceptionWhenPartitionNotFound() {
    // given
    final var request = new TestCommand();
    request.setPartitionId(0);

    // when
    final Future<?> response = client.sendRequestWithRetry(request);

    // then
    final var expected =
        "Expected to execute command on partition 0, but either it does not exist, or the gateway is not yet aware of it";
    assertThat(response)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableThat()
        .withCauseInstanceOf(PartitionNotFoundException.class)
        .withMessageContaining(expected);
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
    final var request = new TestCommand(1L);
    broker
        .onExecuteCommandRequest(TestCommand.VALUE_TYPE, TestCommand.INTENT)
        .respondWith()
        .event()
        .intent(TestCommand.INTENT)
        .key(ExecuteCommandRequest::key)
        .rejection(RejectionType.INVALID_ARGUMENT, "foo")
        .value()
        .allOf(ExecuteCommandRequest::getCommand)
        .done()
        .register();

    // when
    final var responseFuture = client.sendRequestWithRetry(request);

    // then
    assertThat(responseFuture)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableThat()
        .withCause(
            new BrokerRejectionException(
                new BrokerRejection(TestCommand.INTENT, 1, RejectionType.INVALID_ARGUMENT, "foo")));
  }

  @Test
  void shouldReturnRejectionWithCorrectTypeAndReasonAndIgnoreValue() {
    // given
    final var request = new TestCommand(1L);
    broker
        .onExecuteCommandRequest(TestCommand.VALUE_TYPE, TestCommand.INTENT)
        .respondWith()
        .event()
        .intent(TestCommand.INTENT)
        .key(ExecuteCommandRequest::key)
        .rejection(RejectionType.INVALID_ARGUMENT, "foo")
        .value(null) // value is ignored when reading a rejection
        .register();

    // when
    final var responseFuture = client.sendRequestWithRetry(request);

    // then
    assertThat(responseFuture)
        .failsWithin(Duration.ofSeconds(10))
        .withThrowableThat()
        .withCause(
            new BrokerRejectionException(
                new BrokerRejection(TestCommand.INTENT, 1, RejectionType.INVALID_ARGUMENT, "foo")));
  }

  @Test
  void shouldPassTopologyManagerToDispatchStrategy() {
    // given
    final AtomicReference<BrokerTopologyManager> managerRef = new AtomicReference<>();
    final var request =
        new TestCommand(
            1L,
            topologyManager -> {
              managerRef.set(topologyManager);
              return 1;
            });

    // when
    final var responseFuture = client.sendRequest(request);

    // then
    assertThat(responseFuture).failsWithin(Duration.ofSeconds(10));
    assertThat(managerRef).hasValue(topologyManager);
  }

  @Test
  void shouldReceiveJobAvailableNotification() {
    // given
    final AtomicReference<String> messageRef = new AtomicReference<>();
    client.subscribeJobAvailableNotification("foo", messageRef::set);

    // when
    atomixCluster.getEventService().broadcast("foo", "bar");

    // then
    Awaitility.await("until notification received")
        .untilAtomic(messageRef, Matchers.equalTo("bar"));
  }

  @Test
  public void shouldThrowCorrectErrorForInactivePartitionAndNoLeaderRequest() {
    // given
    final var partitionId = 1;
    final var request = new TestCommand(1, topologyManager -> partitionId);

    // when
    broker.updateInfo(info -> info.setInactiveForPartition(partitionId));
    topologyManager.event(new ClusterMembershipEvent(Type.METADATA_CHANGED, broker.member()));
    Awaitility.await("Partition is inactive.")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getInactiveNodesForPartition(1))
                    .isNotEmpty());

    // then
    assertThatCode(() -> client.sendRequest(request).join())
        .isInstanceOf(CompletionException.class)
        .hasMessageContaining("The partition " + partitionId + " is currently INACTIVE");
  }

  @Test
  public void shouldSendRequestToLeaderIfSomePartitionReplicasInactive() {
    // given
    final var partitionId = 1;
    final var leaderBrokerId = 2;
    final var request = new TestCommand(1, topologyManager -> partitionId);

    // when
    broker.updateInfo(info -> info.setInactiveForPartition(partitionId));
    topologyManager.event(new ClusterMembershipEvent(Type.METADATA_CHANGED, broker.member()));

    Awaitility.await("Partition " + partitionId + " is inactive.")
        .untilAsserted(
            () ->
                assertThat(topologyManager.getTopology().getInactiveNodesForPartition(1))
                    .isNotEmpty());

    final BrokerResponse<?> response;
    try (final var otherBroker =
        new StubBroker(leaderBrokerId)
            .start()
            .updateInfo(info -> info.setLeaderForPartition(partitionId, 1))) {
      registerSuccessResponse(otherBroker);

      topologyManager.event(
          new ClusterMembershipEvent(Type.METADATA_CHANGED, otherBroker.member()));
      Awaitility.await("Broker " + leaderBrokerId + " is leader.")
          .untilAsserted(
              () ->
                  assertThat(topologyManager.getTopology().getLeaderForPartition(1))
                      .isEqualTo(leaderBrokerId));

      response = client.sendRequest(request).join();
    }

    // then
    assertThat(response.isResponse()).isTrue();
  }

  private void registerSuccessResponse(final StubBroker broker) {
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
  }

  private void registerError(final StubBroker broker, final ErrorCode code, final String data) {
    broker
        .onExecuteCommandRequest(TestCommand.VALUE_TYPE, TestCommand.INTENT)
        .respondWithError()
        .errorCode(code)
        .errorData(data)
        .register();
  }

  private static class TestCommand extends BrokerExecuteCommand<UnifiedRecordValue> {
    private static final ValueType VALUE_TYPE = ValueType.JOB;
    private static final Intent INTENT = JobIntent.YIELD;

    private final UnifiedRecordValue record;
    private final long key;
    private final RequestDispatchStrategy dispatchStrategy;

    private TestCommand() {
      this(new UnifiedRecordValue(10));
    }

    private TestCommand(final long key) {
      this(new UnifiedRecordValue(10), key);
    }

    private TestCommand(final long key, final RequestDispatchStrategy dispatchStrategy) {
      this(new UnifiedRecordValue(10), key, dispatchStrategy);
    }

    private TestCommand(final UnifiedRecordValue record) {
      this(record, Protocol.encodePartitionId(1, 1));
    }

    private TestCommand(final UnifiedRecordValue record, final long key) {
      this(record, key, null);
    }

    private TestCommand(
        final UnifiedRecordValue record,
        final long key,
        final RequestDispatchStrategy dispatchStrategy) {
      super(VALUE_TYPE, INTENT);
      this.record = record;
      this.key = key;
      this.dispatchStrategy = dispatchStrategy;
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

    @Override
    public Optional<RequestDispatchStrategy> requestDispatchStrategy() {
      return Optional.ofNullable(dispatchStrategy);
    }
  }

  @Nested
  final class RoutingTest {
    @Test
    void shouldRouteToLeaderOfRequestedPartition() {
      // given - a second broker (1), which will respond successfully, and broker 0 which will
      // respond
      // with an error, we expect to get a successful response
      // the request is routed to partition 1 first through the default round robin strategy
      final var request = new TestCommand();
      final BrokerResponse<?> response;
      broker.updateInfo(info -> info.setFollowerForPartition(1));
      registerError(broker, ErrorCode.INTERNAL_ERROR, "test");

      try (final var otherBroker =
          new StubBroker(1).start().updateInfo(info -> info.setLeaderForPartition(1, 1))) {
        registerSuccessResponse(otherBroker);

        topologyManager.event(new ClusterMembershipEvent(Type.METADATA_CHANGED, broker.member()));
        topologyManager.event(new ClusterMembershipEvent(Type.MEMBER_ADDED, otherBroker.member()));
        Awaitility.await("Topology is updated")
            .untilAsserted(
                () -> assertThat(topologyManager.getTopology().getLeaderForPartition(1)).isOne());

        // when
        response = client.sendRequest(request).join();
      }

      // then
      assertThat(response.isResponse()).isTrue();
    }

    @Test
    void shouldRouteRequestBasedOnPartitionId() {
      // given - a second broker (1), which respond successfully for partition 2
      final BrokerResponse<?> response;
      final var request = new TestCommand(1L);
      request.setPartitionId(2);

      try (final var otherBroker = new StubBroker(1, 2).start()) {
        registerSuccessResponse(otherBroker);
        final var updatedClusterConfiguration =
            topologyManager
                .getClusterConfiguration()
                .addMember(
                    otherBroker.member().id(),
                    MemberState.initializeAsActive(Map.of(2, PartitionState.active(2, null))));
        topologyManager.onClusterConfigurationUpdated(updatedClusterConfiguration);
        topologyManager.event(new ClusterMembershipEvent(Type.MEMBER_ADDED, otherBroker.member()));
        Awaitility.await("Topology is updated")
            .untilAsserted(
                () -> assertThat(topologyManager.getTopology().getLeaderForPartition(2)).isOne());

        // when
        response = client.sendRequest(request).join();
      }

      // then
      assertThat(response.isResponse()).isTrue();
    }

    @Test
    void shouldRouteRequestBasedOnDispatchStrategy() {
      // given - a second broker (1), which respond successfully for partition 2
      final BrokerResponse<?> response;
      final var request = new TestCommand(1L, ignored -> 2);

      try (final var otherBroker = new StubBroker(1, 2).start()) {
        registerSuccessResponse(otherBroker);
        topologyManager.event(new ClusterMembershipEvent(Type.MEMBER_ADDED, otherBroker.member()));
        Awaitility.await("Topology is updated")
            .untilAsserted(
                () -> assertThat(topologyManager.getTopology().getLeaderForPartition(2)).isOne());

        // when
        response = client.sendRequest(request).join();
      }

      // then
      assertThat(response.isResponse()).isTrue();
    }

    @Test
    void shouldRouteToDeploymentPartitionAsFallback() {
      // given - a dispatch strategy which returns "null"
      final BrokerResponse<?> response;
      final var request = new TestCommand(1L, ignored -> BrokerClusterState.PARTITION_ID_NULL);
      registerSuccessResponse(broker);

      // when
      response = client.sendRequest(request).join();

      // then
      assertThat(response.isResponse()).isTrue();
    }
  }
}
