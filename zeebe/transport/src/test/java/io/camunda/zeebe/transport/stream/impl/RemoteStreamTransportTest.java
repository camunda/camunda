/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import org.agrona.collections.ArrayUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Usage of actual {@link AtomixCluster} instances here is on purpose. The important part is that we
 * test with an actual {@link io.atomix.cluster.messaging.ClusterCommunicationService} since we're
 * dealing with expected error handling/propagation. If there's ever an easy way to build that
 * without building a whole cluster instance, then we can refactor this.
 */
@AutoCloseResources
final class RemoteStreamTransportTest {
  private final List<Node> nodes = List.of(createNode("sender"), createNode("receiver"));
  private final RecordingBackoffSupplier senderBackoffSupplier = new RecordingBackoffSupplier();

  @AutoCloseResource private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  @AutoCloseResource private final AtomixCluster sender = createClusterNode(nodes.get(0), nodes);

  @AutoCloseResource
  private final RemoteStreamTransport<TestSerializableData> transport =
      new RemoteStreamTransport<>(
          sender.getCommunicationService(),
          new RemoteStreamApiHandler<>(
              new RemoteStreamRegistry<>(RemoteStreamMetrics.noop()),
              buffer -> {
                final var data = new TestSerializableData();
                data.wrap(buffer, 0, buffer.capacity());
                return data;
              }),
          senderBackoffSupplier);

  @AutoCloseResource
  private final ActorScheduler scheduler =
      ActorScheduler.newActorScheduler()
          .setCpuBoundActorThreadCount(1)
          .setIoBoundActorThreadCount(0)
          .build();

  @AutoCloseResource private final AtomixCluster receiver = createClusterNode(nodes.get(1), nodes);

  @BeforeEach
  void beforeEach() {
    sender.start().join();
    receiver.start().join();

    scheduler.start();
    scheduler.submitActor(transport).join();
  }

  @Test
  void shouldNotRetryUnknownMembers() {
    // given
    final var unknown = MemberId.anonymous();

    // when
    final var completed = transport.restartStreams(unknown);

    // then
    assertThat(completed).succeedsWithin(Duration.ofSeconds(5));
    assertThat(senderBackoffSupplier.recorded).as("no retries executed").isEmpty();
  }

  @Test
  void shouldNotRetryIfMemberNotHandlingRequest() {
    // given
    // when
    final var completed =
        transport.restartStreams(receiver.getMembershipService().getLocalMember().id());

    // then
    assertThat(completed).succeedsWithin(Duration.ofSeconds(5));
    assertThat(senderBackoffSupplier.recorded).as("no retries executed").isEmpty();
  }

  @Test
  void shouldRetryOnError() throws Exception {
    // given
    receiver.stop().join();

    // when
    final var completed =
        transport.restartStreams(receiver.getMembershipService().getLocalMember().id());
    Awaitility.await("until we've retried at least twice")
        .pollInSameThread()
        .untilAsserted(
            () -> assertThat(senderBackoffSupplier.recorded).hasSizeGreaterThanOrEqualTo(2));
    try (final var newReceiver = createClusterNode(nodes.get(1), nodes)) {
      newReceiver.start().join();
      newReceiver
          .getCommunicationService()
          .replyTo(
              StreamTopics.RESTART_STREAMS.topic(),
              Function.identity(),
              (id, ignored) -> ArrayUtil.EMPTY_BYTE_ARRAY,
              Function.identity(),
              Runnable::run);

      // then
      assertThat(completed).succeedsWithin(Duration.ofSeconds(5));
      assertThat(senderBackoffSupplier.recorded).startsWith(100L, 200L);
    }
  }

  @Test
  void shouldNotRetryOnRemoteHandlerFailure() {
    // given
    receiver
        .getCommunicationService()
        .replyTo(
            StreamTopics.RESTART_STREAMS.topic(),
            Function.identity(),
            (id, ignored) -> {
              throw new RuntimeException("I have become error, destroyer of worlds");
            },
            Function.identity(),
            Runnable::run);

    // when
    final var completed =
        transport.restartStreams(receiver.getMembershipService().getLocalMember().id());

    // then
    assertThat(completed)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .havingRootCause()
        .isInstanceOf(RemoteHandlerFailure.class);
    assertThat(senderBackoffSupplier.recorded).isEmpty();
  }

  private Node createNode(final String id) {
    return Node.builder().withId(id).withPort(SocketUtil.getNextAddress().getPort()).build();
  }

  private AtomixCluster createClusterNode(final Node localNode, final Collection<Node> nodes) {
    return AtomixCluster.builder(meterRegistry)
        .withAddress(localNode.address())
        .withMemberId(localNode.id().id())
        .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
        .withMembershipProtocol(new DiscoveryMembershipProtocol())
        .build();
  }

  // a back off supplier which records given delays and returns them multiplied by 2
  private static final class RecordingBackoffSupplier implements LongUnaryOperator {
    private final List<Long> recorded = new ArrayList<>();

    @Override
    public long applyAsLong(final long operand) {
      recorded.add(operand);
      return operand * 2;
    }
  }
}
