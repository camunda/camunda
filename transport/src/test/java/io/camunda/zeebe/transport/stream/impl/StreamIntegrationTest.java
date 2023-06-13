/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure;
import io.atomix.utils.Managed;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.api.ClientStreamService;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.transport.stream.api.RemoteStreamErrorHandler;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests end-to-end stream management from client to server */
final class StreamIntegrationTest {

  private ClientStreamService<TestSerializableData> clientService;
  private RemoteStreamService<TestSerializableData, TestSerializableData> serverService;
  private TestActor serverActor;
  private ClientStreamer<TestSerializableData> clientStreamer;
  private RemoteStreamer<TestSerializableData, TestSerializableData> remoteStreamer;

  private final DirectBuffer streamType = BufferUtil.wrapString("foo");
  private final TestSerializableData metadata = new TestSerializableData(1);
  private final ActorScheduler actorScheduler =
      ActorScheduler.newActorScheduler()
          .setCpuBoundActorThreadCount(1)
          .setIoBoundActorThreadCount(1)
          .build();
  private final TestCluster cluster = createCluster();
  private RemoteStreamErrorHandler<TestSerializableData> errorHandler = (e, d) -> {};

  @BeforeEach
  void setup() {
    actorScheduler.start();
    cluster.start();

    final var factory = new TransportFactory(actorScheduler);
    clientService =
        factory.createRemoteStreamClient(
            cluster.clientNode().getCommunicationService(), ClientStreamMetrics.noop());
    clientService.start(actorScheduler).join();
    clientStreamer = clientService.streamer();

    serverActor = new TestActor();
    actorScheduler.submitActor(serverActor).join();
    // indirectly reference the error handler to allow swapping its behavior during tests
    final RemoteStreamErrorHandler<TestSerializableData> dynamicErrorHandler =
        (e, d) -> errorHandler.handleError(e, d);
    serverService =
        factory.createRemoteStreamServer(
            cluster.serverNode().getCommunicationService(),
            TestSerializableData::new,
            dynamicErrorHandler,
            RemoteStreamMetrics.noop());
    this.remoteStreamer =
        serverActor.call(() -> serverService.start(actorScheduler, serverActor)).join().join();
    clientService.onServerJoined(cluster.serverNode().getMembershipService().getLocalMember().id());
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(
        () -> clientService.closeAsync().join(),
        () -> serverService.closeAsync(serverActor),
        serverActor,
        cluster,
        actorScheduler);
  }

  @Test
  void shouldAddRemoteStream() {
    // given
    final var streamType = BufferUtil.wrapString("foo");
    final var properties = new TestSerializableData();
    final var serverMemberId = cluster.serverNode().getMembershipService().getLocalMember().id();

    // when
    final var streamId =
        clientStreamer
            .add(streamType, properties, p -> CompletableFuture.completedFuture(null))
            .join();

    // then
    Awaitility.await("until stream is registered server side")
        .untilAsserted(() -> assertThat(remoteStreamer.streamFor(streamType)).isPresent());
    Awaitility.await("until stream is registered client side")
        .untilAsserted(
            () ->
                Assertions.assertThat(clientService.streamFor(streamId).join())
                    .hasValueSatisfying(s -> s.isConnected(serverMemberId)));
  }

  @Test
  void shouldRemoveRemoteStream() {
    // given
    final var streamType = BufferUtil.wrapString("foo");
    final var properties = new TestSerializableData();
    final var streamId =
        clientStreamer
            .add(streamType, properties, p -> CompletableFuture.completedFuture(null))
            .join();
    final var serverMemberId = cluster.serverNode().getMembershipService().getLocalMember().id();

    // must wait until the stream is connected everywhere before removal, as otherwise there is a
    // race condition
    Awaitility.await("until stream is registered client side")
        .untilAsserted(
            () ->
                Assertions.assertThat(clientService.streamFor(streamId).join())
                    .hasValueSatisfying(s -> s.isConnected(serverMemberId)));

    // when
    clientStreamer.remove(streamId).join();

    // then
    Awaitility.await("until stream is removed from the server side")
        .untilAsserted(() -> assertThat(remoteStreamer.streamFor(streamType)).isEmpty());
    Awaitility.await("until stream is removed from the client side")
        .untilAsserted(
            () -> Assertions.assertThat(clientService.streamFor(streamId).join()).isEmpty());
  }

  @Test
  void shouldReceiveStreamPayloads() throws InterruptedException {
    // given
    final AtomicReference<List<Integer>> payloads = new AtomicReference<>(new ArrayList<>());
    final CountDownLatch latch = new CountDownLatch(2);

    clientStreamer
        .add(
            streamType,
            metadata,
            p -> {
              final TestSerializableData payload = new TestSerializableData();
              payload.wrap(p, 0, p.capacity());
              payloads.get().add(payload.data());
              latch.countDown();
              return CompletableFuture.completedFuture(null);
            })
        .join();

    // when
    Awaitility.await().until(() -> remoteStreamer.streamFor(streamType).isPresent());

    pushPayload(new TestSerializableData().data(100));
    pushPayload(new TestSerializableData().data(200));

    // then
    // verify client receives payload
    latch.await();
    assertThat(payloads.get()).asList().containsExactly(100, 200);
  }

  @Test
  void shouldReturnErrorWhenClientStreamIsClosed() throws InterruptedException {
    // given
    final AtomicReference<Throwable> error = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);
    final var clientStreamId =
        clientStreamer
            .add(streamType, metadata, p -> CompletableFuture.completedFuture(null))
            .join();
    Awaitility.await().until(() -> remoteStreamer.streamFor(streamType).isPresent());
    final var serverStream = remoteStreamer.streamFor(streamType).orElseThrow();
    errorHandler =
        (e, p) -> {
          error.set(e);
          latch.countDown();
        };

    // when
    clientStreamer.remove(clientStreamId);

    // Use serverStream obtained before stream is removed
    serverStream.push(new TestSerializableData(100));

    // then - we can't assert for NoSuchStreamException on the server side, as we don't serialize
    // the exceptions when transmitting them
    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(error.get()).hasRootCauseInstanceOf(RemoteHandlerFailure.class);
  }

  private void pushPayload(final TestSerializableData data) {
    remoteStreamer.streamFor(streamType).orElseThrow().push(data);
  }

  private TestCluster createCluster() {
    final var serverNode =
        Node.builder().withId("server").withPort(SocketUtil.getNextAddress().getPort()).build();
    final var clientNode =
        Node.builder().withId("client").withPort(SocketUtil.getNextAddress().getPort()).build();
    final var clusterNodes = List.of(serverNode, clientNode);

    return new TestCluster(
        createClusterNode(serverNode, clusterNodes), createClusterNode(clientNode, clusterNodes));
  }

  private AtomixCluster createClusterNode(final Node localNode, final Collection<Node> nodes) {
    return AtomixCluster.builder()
        .withAddress(localNode.address())
        .withMemberId(localNode.id().id())
        .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
        .withMembershipProtocol(new DiscoveryMembershipProtocol())
        .build();
  }

  private static final class TestActor extends Actor {}

  private record TestCluster(AtomixCluster serverNode, AtomixCluster clientNode)
      implements AutoCloseable {

    private void start() {
      Stream.of(serverNode, clientNode).map(Managed::start).forEach(CompletableFuture::join);
    }

    @Override
    public void close() {
      Stream.of(serverNode, clientNode).map(Managed::stop).forEach(CompletableFuture::join);
    }
  }
}
