/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.rebalance.RebalanceErrorResponse.RebalanceErrorCode;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.ConfigurationChangeInProgressException;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.NotCoordinatorException;
import io.camunda.zeebe.rebalance.RebalanceRequestFailedException.RebalanceInProgressException;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the hop an operator request makes from the member that received it to the coordinator.
 *
 * <p>The cluster is shared by every test because each node assigns a port, and the tests only
 * differ in what the coordinator answers.
 */
final class RebalanceForwardingTest {

  private static final MemberId GATEWAY_ID = MemberId.from("gateway");
  private static final MemberId COORDINATOR_ID = MemberId.from("0");

  private static final MeterRegistry REGISTRY = new SimpleMeterRegistry();
  private static final RecordingRebalanceApi COORDINATOR = new RecordingRebalanceApi();

  private static AtomixCluster gatewayNode;
  private static AtomixCluster coordinatorNode;
  private static RebalanceRequestServer requestServer;
  private static RebalanceRequestSender sender;

  @BeforeAll
  static void startCluster() {
    final var gateway =
        Node.builder()
            .withId(GATEWAY_ID.id())
            .withPort(SocketUtil.getNextAddress().getPort())
            .build();
    final var coordinator =
        Node.builder()
            .withId(COORDINATOR_ID.id())
            .withPort(SocketUtil.getNextAddress().getPort())
            .build();
    final var nodes = List.of(gateway, coordinator);

    gatewayNode = createClusterNode(gateway, nodes);
    coordinatorNode = createClusterNode(coordinator, nodes);
    CompletableFuture.allOf(gatewayNode.start(), coordinatorNode.start()).join();

    sender =
        new RebalanceRequestSender(
            gatewayNode.getCommunicationService(),
            ClusterConfigurationCoordinatorSupplier.ofMembers(Set.of(COORDINATOR_ID)),
            new ProtoBufRebalanceSerializer());
    requestServer =
        new RebalanceRequestServer(
            coordinatorNode.getCommunicationService(),
            new ProtoBufRebalanceSerializer(),
            COORDINATOR);
    requestServer.start();
  }

  @AfterAll
  static void stopCluster() {
    requestServer.close();
    CompletableFuture.allOf(gatewayNode.stop(), coordinatorNode.stop()).join();
    REGISTRY.close();
  }

  @BeforeEach
  void resetCoordinator() {
    COORDINATOR.reset();
  }

  @Test
  void shouldForwardATriggerToTheCoordinator() {
    // given
    final var request =
        new TriggerRebalanceRequest(
            new RebalanceOverrides(2048L, Duration.ofSeconds(20), 3, null), true);
    COORDINATOR.status =
        new RebalanceStatus(
            new RebalanceStatus.Running(
                9, request.overrides(), true, false, List.of(), Instant.EPOCH),
            null,
            RebalanceStatus.idle().leadershipStatus());

    // when
    final var response = sender.triggerRebalance(request).join();

    // then
    assertThat(COORDINATOR.triggered).isEqualTo(request);
    assertThat(response.get()).isEqualTo(COORDINATOR.status);
  }

  @Test
  void shouldForwardAStatusQuery() {
    // given
    COORDINATOR.status =
        new RebalanceStatus(
            null,
            new RebalanceStatus.Completed(
                8,
                RebalanceOutcome.COMPLETED,
                List.of(PartitionRebalance.alreadyLeader("default", 1, MemberId.from("1"))),
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:05Z")),
            RebalanceStatus.idle().leadershipStatus());

    // when
    final var response = sender.getRebalanceStatus().join();

    // then
    assertThat(response.get()).isEqualTo(COORDINATOR.status);
  }

  @Test
  void shouldForwardACancellation() {
    // given
    COORDINATOR.wasRunning = true;

    // when
    final var response = sender.cancelRebalance().join();

    // then
    assertThat(COORDINATOR.cancelled).isTrue();
    assertThat(response.get()).isEqualTo(new CancelRebalanceResponse(true));
  }

  @Test
  void shouldReportARefusedTrigger() {
    // given
    COORDINATOR.failure = new RebalanceInProgressException("Rebalance 7 is already running");

    // when
    final var response =
        sender.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings()).join();

    // then
    assertThat(response.getLeft())
        .isEqualTo(
            new RebalanceErrorResponse(
                RebalanceErrorCode.REBALANCE_IN_PROGRESS, "Rebalance 7 is already running"));
  }

  @Test
  void shouldReportARefusalDueToAPendingConfigurationChange() {
    // given
    COORDINATOR.failure =
        new ConfigurationChangeInProgressException(
            "Cannot start a rebalance while a cluster configuration change is in progress");

    // when
    final var response =
        sender.triggerRebalance(TriggerRebalanceRequest.withConfiguredSettings()).join();

    // then
    assertThat(response.getLeft())
        .isEqualTo(
            new RebalanceErrorResponse(
                RebalanceErrorCode.CONFIGURATION_CHANGE_IN_PROGRESS,
                "Cannot start a rebalance while a cluster configuration change is in progress"));
  }

  @Test
  void shouldReportARequestThatReachedANonCoordinator() {
    // given
    COORDINATOR.failure =
        new NotCoordinatorException("Member 0 is not the rebalancing coordinator");

    // when
    final var response = sender.getRebalanceStatus().join();

    // then
    assertThat(response.getLeft().code()).isEqualTo(RebalanceErrorCode.NOT_COORDINATOR);
  }

  @Test
  void shouldReportAnUnexpectedFailureAsInternal() {
    // given
    COORDINATOR.failure = new IllegalStateException("actor is closed");

    // when
    final var response = sender.cancelRebalance().join();

    // then
    assertThat(response.getLeft())
        .isEqualTo(
            new RebalanceErrorResponse(RebalanceErrorCode.INTERNAL_ERROR, "actor is closed"));
  }

  private static AtomixCluster createClusterNode(final Node localNode, final List<Node> nodes) {
    return AtomixCluster.builder(REGISTRY)
        .withMemberId(localNode.id().id())
        .withAddress(localNode.address())
        .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
        .withMembershipProtocol(new DiscoveryMembershipProtocol())
        .build();
  }

  private static final class RecordingRebalanceApi implements RebalanceApi {

    private RebalanceStatus status = RebalanceStatus.idle();
    private boolean wasRunning;
    private RuntimeException failure;

    private TriggerRebalanceRequest triggered;
    private boolean cancelled;

    void reset() {
      status = RebalanceStatus.idle();
      wasRunning = false;
      failure = null;
      triggered = null;
      cancelled = false;
    }

    @Override
    public ActorFuture<RebalanceStatus> triggerRebalance(final TriggerRebalanceRequest request) {
      triggered = request;
      return answerWith(status);
    }

    @Override
    public ActorFuture<RebalanceStatus> getRebalanceStatus() {
      return answerWith(status);
    }

    @Override
    public ActorFuture<CancelRebalanceResponse> cancelRebalance() {
      cancelled = true;
      return answerWith(new CancelRebalanceResponse(wasRunning));
    }

    private <T> ActorFuture<T> answerWith(final T answer) {
      return failure != null
          ? CompletableActorFuture.completedExceptionally(failure)
          : CompletableActorFuture.completed(answer);
    }
  }
}
