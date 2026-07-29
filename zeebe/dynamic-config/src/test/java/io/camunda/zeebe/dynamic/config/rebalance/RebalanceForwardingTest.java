/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceErrorResponse.RebalanceErrorCode;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceRequestFailedException.NotCoordinator;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceRequestFailedException.RebalanceInProgress;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the hop an operator request makes from the member that received it to the coordinator: one
 * cluster of two nodes, the sender on the one an operator reached and the server on the
 * coordinator.
 *
 * <p>The cluster is shared by every test because each node costs a port, and the tests only differ
 * in what the coordinator answers.
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
    gatewayNode.stop();
    coordinatorNode.stop();
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
        new TriggerRebalanceRequest(new RebalanceOverrides(2048L, Duration.ofSeconds(20), 3), true);
    COORDINATOR.status =
        new RebalanceStatus(new RebalanceStatus.Running(9, request.overrides(), true, false), null);

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
        new RebalanceStatus(null, new RebalanceStatus.Completed(8, RebalanceOutcome.COMPLETED));

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
    COORDINATOR.failure = new RebalanceInProgress("Rebalance 7 is already running");

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
  void shouldReportARequestThatReachedANonCoordinator() {
    // given
    COORDINATOR.failure = new NotCoordinator("Member 0 is not the rebalancing coordinator");

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
