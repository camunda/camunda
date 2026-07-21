/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.messages.AddStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.RemoveStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.StreamTopics;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import org.agrona.CloseHelper;
import org.agrona.collections.ArrayUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Usage of actual {@link AtomixCluster} instances here is on purpose. The important part is that we
 * test with an actual {@link io.atomix.cluster.messaging.ClusterCommunicationService} since we're
 * dealing with expected error handling/propagation. If there's ever an easy way to build that
 * without building a whole cluster instance, then we can refactor this.
 */
final class RemoteStreamTransportTest {
  private final List<Node> nodes = List.of(createNode("sender"), createNode("receiver"));
  private final RecordingBackoffSupplier senderBackoffSupplier = new RecordingBackoffSupplier();
  private final ActorScheduler scheduler =
      ActorScheduler.newActorScheduler()
          .setCpuBoundActorThreadCount(1)
          .setIoBoundActorThreadCount(0)
          .build();
  @AutoClose private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final AtomixCluster sender = createClusterNode(nodes.get(0), nodes);
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
          senderBackoffSupplier,
          DEFAULT_PHYSICAL_TENANT_ID);
  private final AtomixCluster receiver = createClusterNode(nodes.get(1), nodes);

  @AfterEach
  void afterEach() {
    // We need to make sure that the Actor is closed last, otherwise we end up in a deadlock
    // Thus, usage of @AutoClose is not possible, as there are no guarantees about ordering
    CloseHelper.quietCloseAll(transport, sender, receiver, scheduler);
  }

  @BeforeEach
  void beforeEach() {
    sender.start().join();
    receiver.start().join();

    scheduler.start();
    scheduler.submitActor(transport).join();
  }

  @Test
  void shouldNotRetryUnknownMembers() throws Exception {
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
    // given - the primary and legacy channels both retry independently against the stopped
    // receiver, so backoff entries from both interleave; we only assert that retries happened
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
      final var newCommunicationService = newReceiver.getCommunicationService();
      newCommunicationService.replyTo(
          StreamTopics.RESTART_STREAMS.topic(DEFAULT_PHYSICAL_TENANT_ID),
          Function.identity(),
          (id, ignored) -> ArrayUtil.EMPTY_BYTE_ARRAY,
          Function.identity(),
          Runnable::run);
      newCommunicationService.replyTo(
          StreamTopics.RESTART_STREAMS.legacyTopic(),
          Function.identity(),
          (id, ignored) -> ArrayUtil.EMPTY_BYTE_ARRAY,
          Function.identity(),
          Runnable::run);

      // then
      assertThat(completed).succeedsWithin(Duration.ofSeconds(5));
      assertThat(senderBackoffSupplier.recorded).hasSizeGreaterThanOrEqualTo(2).contains(100L);
    }
  }

  @Test
  void shouldNotRetryOnRemoteHandlerFailure() {
    // given - both the primary and legacy topics must fail the same way, otherwise the legacy
    // channel would get NoRemoteHandler (no handler registered) and race completed with a false
    // success
    final BiFunction<MemberId, byte[], byte[]> throwingHandler =
        (id, ignored) -> {
          throw new RuntimeException("I have become error, destroyer of worlds");
        };
    receiver
        .getCommunicationService()
        .replyTo(
            StreamTopics.RESTART_STREAMS.topic(DEFAULT_PHYSICAL_TENANT_ID),
            Function.identity(),
            throwingHandler,
            Function.identity(),
            Runnable::run);
    receiver
        .getCommunicationService()
        .replyTo(
            StreamTopics.RESTART_STREAMS.legacyTopic(),
            Function.identity(),
            throwingHandler,
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

  @Test
  void shouldReplyOnLegacyAddRemoveAndRemoveAllTopicsForDefaultTenant() {
    // given
    final var streamId = UUID.randomUUID();
    final var senderMemberId = sender.getMembershipService().getLocalMember().id();

    // when / then - ADD
    final var addRequest =
        new AddStreamRequest()
            .streamId(streamId)
            .streamType(new UnsafeBuffer(BufferUtil.wrapString("foo")))
            .metadata(new UnsafeBuffer(new byte[Integer.BYTES]));
    final var addResponse =
        receiver
            .getCommunicationService()
            .send(
                StreamTopics.ADD.legacyTopic(),
                BufferUtil.bufferAsArray(addRequest),
                Function.identity(),
                Function.identity(),
                senderMemberId,
                Duration.ofSeconds(5));
    assertThat(addResponse).succeedsWithin(Duration.ofSeconds(5));

    // when / then - REMOVE
    final var removeRequest = new RemoveStreamRequest().streamId(streamId);
    final var removeResponse =
        receiver
            .getCommunicationService()
            .send(
                StreamTopics.REMOVE.legacyTopic(),
                BufferUtil.bufferAsArray(removeRequest),
                Function.identity(),
                Function.identity(),
                senderMemberId,
                Duration.ofSeconds(5));
    assertThat(removeResponse).succeedsWithin(Duration.ofSeconds(5));

    // when / then - REMOVE_ALL
    final var removeAllResponse =
        receiver
            .getCommunicationService()
            .send(
                StreamTopics.REMOVE_ALL.legacyTopic(),
                ArrayUtil.EMPTY_BYTE_ARRAY,
                Function.identity(),
                Function.identity(),
                senderMemberId,
                Duration.ofSeconds(5));
    assertThat(removeAllResponse).succeedsWithin(Duration.ofSeconds(5));
  }

  @Test
  void shouldNotReplyOnLegacyTopicsForNonDefaultTenant() {
    // given
    CloseHelper.quietClose(transport);
    final var nonDefaultTransport =
        new RemoteStreamTransport<>(
            sender.getCommunicationService(),
            new RemoteStreamApiHandler<>(
                new RemoteStreamRegistry<>(RemoteStreamMetrics.noop()),
                buffer -> {
                  final var data = new TestSerializableData();
                  data.wrap(buffer, 0, buffer.capacity());
                  return data;
                }),
            senderBackoffSupplier,
            "tenant1");
    scheduler.submitActor(nonDefaultTransport).join();
    try {
      // when
      final var addResponse =
          receiver
              .getCommunicationService()
              .send(
                  StreamTopics.ADD.legacyTopic(),
                  ArrayUtil.EMPTY_BYTE_ARRAY,
                  Function.identity(),
                  Function.identity(),
                  sender.getMembershipService().getLocalMember().id(),
                  Duration.ofSeconds(5));

      // then - no handler registered on the legacy topic for a non-default tenant
      assertThat(addResponse).failsWithin(Duration.ofSeconds(5));
    } finally {
      CloseHelper.quietClose(nonDefaultTransport);
    }
  }

  @Test
  void shouldSendLegacyRestartStreamsRequestForDefaultTenant() {
    // given
    final var legacyRestartReceived = new AtomicBoolean(false);
    receiver
        .getCommunicationService()
        .replyTo(
            StreamTopics.RESTART_STREAMS.legacyTopic(),
            Function.identity(),
            (id, ignored) -> {
              legacyRestartReceived.set(true);
              return ArrayUtil.EMPTY_BYTE_ARRAY;
            },
            Function.identity(),
            Runnable::run);

    // when
    final var completed =
        transport.restartStreams(receiver.getMembershipService().getLocalMember().id());

    // then
    assertThat(completed).succeedsWithin(Duration.ofSeconds(5));
    Awaitility.await("until the legacy RESTART_STREAMS request is received")
        .untilTrue(legacyRestartReceived);
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
