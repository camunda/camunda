/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEvent.Type;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStreamMetrics;
import io.camunda.zeebe.transport.stream.api.ClientStreamService;
import io.camunda.zeebe.transport.stream.api.ClientStreamer;
import io.camunda.zeebe.transport.stream.api.RemoteStream;
import io.camunda.zeebe.transport.stream.api.RemoteStreamErrorHandler;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import io.camunda.zeebe.transport.stream.api.StreamResponseException;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests end-to-end stream management from client to server */
final class StreamIntegrationTest {
  private final ActorScheduler actorScheduler =
      ActorScheduler.newActorScheduler()
          .setCpuBoundActorThreadCount(2)
          .setIoBoundActorThreadCount(1)
          .build();
  private final List<Node> clusterNodes =
      List.of(createNode("server1"), createNode("server2"), createNode("client"));
  private final TestSerializableData metadata = new TestSerializableData(1);
  private ClientStreamer<TestSerializableData> clientStreamer;
  @AutoClose private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TestServer server1 =
      new TestServer(createClusterNode(clusterNodes.get(0), clusterNodes));
  private final TestServer server2 =
      new TestServer(createClusterNode(clusterNodes.get(1), clusterNodes));
  private final TestClient client =
      new TestClient(createClusterNode(clusterNodes.get(2), clusterNodes));

  @BeforeEach
  void setup() {
    actorScheduler.start();
    server1.start();
    server2.start();
    client.start();

    client.streamService.onServerJoined(
        server1.cluster.getMembershipService().getLocalMember().id());
    client.streamService.onServerJoined(
        server2.cluster.getMembershipService().getLocalMember().id());
    clientStreamer = client.streamService.streamer();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(client, server1, server2, actorScheduler);
  }

  @Test
  void shouldReceiveStreamPayloads() throws InterruptedException {
    // given
    final AtomicReference<List<Integer>> payloads = new AtomicReference<>(new ArrayList<>());
    final var latch = new CountDownLatch(2);
    final var streamType = BufferUtil.wrapString("foo");
    final var streamId =
        clientStreamer
            .add(
                streamType,
                metadata,
                p -> {
                  final TestSerializableData payload = new TestSerializableData();
                  payload.wrap(p, 0, p.capacity());
                  payloads.get().add(payload.data());
                  latch.countDown();
                  return CompletableActorFuture.completed(null);
                })
            .join();
    awaitStreamAdded(streamType, streamId, server1, server2);

    // when
    server1.streamer.streamFor(streamType).orElseThrow().push(new TestSerializableData().data(100));
    server1.streamer.streamFor(streamType).orElseThrow().push(new TestSerializableData().data(200));

    // then
    // verify client receives payload
    latch.await();
    assertThat(payloads.get()).asList().containsExactly(100, 200);
  }

  @Test
  void shouldReturnErrorWhenClientStreamIsClosed() throws InterruptedException {
    // given
    final AtomicReference<Throwable> error = new AtomicReference<>();
    final var latch = new CountDownLatch(1);
    final var streamType = BufferUtil.wrapString("foo");
    final var clientStreamId =
        clientStreamer
            .add(streamType, metadata, p -> CompletableActorFuture.completed(null))
            .join();
    awaitStreamAdded(streamType, clientStreamId, server1, server2);
    final var serverStream = server1.streamer.streamFor(streamType).orElseThrow();
    server1.errorHandler =
        (e, p) -> {
          error.set(e);
          latch.countDown();
        };

    // when
    clientStreamer.remove(clientStreamId);

    // Use serverStream obtained before stream is removed
    serverStream.push(new TestSerializableData(100));

    // then
    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(error.get())
        .isInstanceOf(StreamResponseException.class)
        .asInstanceOf(InstanceOfAssertFactories.throwable(StreamResponseException.class))
        .returns(ErrorCode.NOT_FOUND, StreamResponseException::code);
  }

  private Node createNode(final String id) {
    return Node.builder().withId(id).withPort(SocketUtil.getNextAddress().getPort()).build();
  }

  private void awaitStreamAdded(
      final DirectBuffer streamType, final ClientStreamId streamId, final TestServer... servers) {
    final Set<MemberId> addedIds = new HashSet<>();
    for (final var server : servers) {
      awaitStreamOnServer(streamType, server, stream -> assertThat(stream).isPresent());
      addedIds.add(server.memberId());
    }

    awaitStreamOnClient(
        streamId,
        stream ->
            assertThat(stream)
                .map(ClientStream::liveConnections)
                .hasValueSatisfying(s -> assertThat(s).containsAll(addedIds)));
  }

  private void awaitStreamOnServer(
      final DirectBuffer streamType,
      final TestServer server,
      final Consumer<Optional<RemoteStream<TestSerializableData, TestSerializableData>>>
          assertions) {
    Awaitility.await()
        .untilAsserted(() -> assertions.accept(server.streamer.streamFor(streamType)));
  }

  private void awaitStreamOnClient(
      final ClientStreamId streamId,
      final Consumer<Optional<ClientStream<TestSerializableData>>> assertions) {
    Awaitility.await().untilAsserted(() -> assertStreamOnClient(streamId, assertions));
  }

  private void assertStreamOnClient(
      final ClientStreamId streamId,
      final Consumer<Optional<ClientStream<TestSerializableData>>> assertions) {
    assertions.accept(client.streamService.streamFor(streamId).join());
  }

  private AtomixCluster createClusterNode(final Node localNode, final Collection<Node> nodes) {
    return AtomixCluster.builder(meterRegistry)
        .withAddress(localNode.address())
        .withMemberId(localNode.id().id())
        .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
        .withMembershipProtocol(new DiscoveryMembershipProtocol())
        .build();
  }

  @Test
  void shouldRegisterStreamsAgainInCaseOfAsymmetricPartition() {
    // given
    final var streamType = BufferUtil.wrapString("foo");
    final var properties = new TestSerializableData();
    final var streamId =
        clientStreamer
            .add(streamType, properties, p -> CompletableActorFuture.completed(null))
            .join();
    final var member = client.cluster.getMembershipService().getLocalMember();
    final var memberRemovedEvent = new ClusterMembershipEvent(Type.MEMBER_REMOVED, member);
    final var memberAddedEvent = new ClusterMembershipEvent(Type.MEMBER_ADDED, member);
    awaitStreamAdded(streamType, streamId, server1, server2);

    // when - remove client from server's members, then add it back, and expect it to be
    // re-registered
    assertThat(server1.streamService.isRelevant(memberRemovedEvent)).isTrue();
    server1.streamService.event(memberRemovedEvent);
    awaitStreamOnServer(streamType, server1, stream -> assertThat(stream).isEmpty());
    assertThat(server1.streamService.isRelevant(memberAddedEvent)).isTrue();
    server1.streamService.event(memberAddedEvent);

    awaitStreamAdded(streamType, streamId, server1, server2);
  }

  @Nested
  final class LifecycleTest {
    @Test
    void shouldAddRemoteStream() {
      // given
      final var streamType = BufferUtil.wrapString("foo");
      final var properties = new TestSerializableData();

      // when
      final var streamId =
          clientStreamer
              .add(streamType, properties, p -> CompletableActorFuture.completed(null))
              .join();

      // then
      awaitStreamAdded(streamType, streamId, server1, server2);
    }

    @Test
    void shouldRemoveRemoteStream() {
      // given
      final var streamType = BufferUtil.wrapString("foo");
      final var properties = new TestSerializableData();
      final var streamId =
          clientStreamer
              .add(streamType, properties, p -> CompletableActorFuture.completed(null))
              .join();

      // must wait until the stream is connected everywhere before removal, as otherwise there is a
      // race condition
      awaitStreamAdded(streamType, streamId, server1, server2);

      // when
      clientStreamer.remove(streamId).join();

      // then
      awaitStreamOnServer(streamType, server1, stream -> assertThat(stream).isEmpty());
      awaitStreamOnServer(streamType, server2, stream -> assertThat(stream).isEmpty());
      awaitStreamOnClient(streamId, stream -> assertThat(stream).isEmpty());
      awaitStreamOnClient(streamId, stream -> assertThat(stream).isEmpty());
    }

    @Test
    void shouldDisconnectFromDeadServer() {
      // given
      final var streamType = BufferUtil.wrapString("foo");
      final var properties = new TestSerializableData();
      final var streamId =
          clientStreamer
              .add(streamType, properties, p -> CompletableActorFuture.completed(null))
              .join();
      awaitStreamAdded(streamType, streamId, server1, server2);

      // when
      client.streamService.onServerRemoved(server1.memberId());

      // then
      awaitStreamOnClient(
          streamId,
          stream ->
              assertThat(stream)
                  .map(ClientStream::liveConnections)
                  .hasValue(Set.of(server2.memberId())));
    }

    @Test
    void shouldRegisterStreamOnReconnectedServer() {
      // given
      final var streamType = BufferUtil.wrapString("foo");
      final var properties = new TestSerializableData();
      client.streamService.onServerRemoved(server1.memberId());
      final var streamId =
          clientStreamer
              .add(streamType, properties, p -> CompletableActorFuture.completed(null))
              .join();
      awaitStreamOnServer(streamType, server2, stream -> assertThat(stream).isPresent());
      awaitStreamOnClient(
          streamId,
          stream ->
              assertThat(stream)
                  .map(ClientStream::liveConnections)
                  .hasValue(Set.of(server2.memberId())));

      // when
      client.streamService.onServerJoined(server1.memberId());

      // then
      awaitStreamAdded(streamType, streamId, server1, server2);
    }

    @Test
    void shouldRemoveAllStreamsOnClientShutdown() {
      // given - use different stream types so we can deterministically introspect multiple streams
      // with a RemoteStreamer
      final var streamTypes =
          List.of(
              BufferUtil.wrapString("foo"),
              BufferUtil.wrapString("bar"),
              BufferUtil.wrapString("buz"));
      final var properties = new TestSerializableData();
      final ClientStreamConsumer consumer = p -> CompletableActorFuture.completed(null);
      streamTypes.forEach(
          streamType -> {
            final var id = clientStreamer.add(streamType, properties, consumer).join();
            awaitStreamAdded(streamType, id, server1, server2);
          });

      // when
      client.streamService.closeAsync().join();

      // then - can't really test with other streams to other clients for a given type due to the
      // RemoteStreamer interface, but it's fine for now.
      streamTypes.forEach(
          streamType -> {
            awaitStreamOnServer(streamType, server1, stream -> assertThat(stream).isEmpty());
            awaitStreamOnServer(streamType, server2, stream -> assertThat(stream).isEmpty());
          });
    }

    @Test
    void shouldAddStreamAgainOnServerRestart() {
      // given
      final var streamType = BufferUtil.wrapString("foo");
      final var properties = new TestSerializableData();
      final var streamId =
          clientStreamer
              .add(streamType, properties, p -> CompletableActorFuture.completed(null))
              .join();
      awaitStreamAdded(streamType, streamId, server1, server2);
      server1.close();
      client.streamService.onServerRemoved(server1.memberId());
      awaitStreamOnClient(
          streamId,
          stream ->
              assertThat(stream)
                  .map(ClientStream::liveConnections)
                  .hasValue(Set.of(server2.memberId())));

      // when - simulate restart by recreating the server entirely
      try (final var restartedServer =
          new TestServer(createClusterNode(clusterNodes.get(0), clusterNodes))) {
        restartedServer.start();
        client.streamService.onServerJoined(restartedServer.memberId());

        // then
        awaitStreamAdded(streamType, streamId, restartedServer, server2);
      }
    }
  }

  private final class TestServer extends Actor {
    private final AtomixCluster cluster;
    private final RemoteStreamService<TestSerializableData, TestSerializableData> streamService;

    private RemoteStreamErrorHandler<TestSerializableData> errorHandler = (e, d) -> {};
    private RemoteStreamer<TestSerializableData, TestSerializableData> streamer;

    private TestServer(final AtomixCluster cluster) {
      this.cluster = cluster;

      final var factory = new TransportFactory(actorScheduler);

      // indirectly reference the error handler to allow swapping its behavior during tests
      final RemoteStreamErrorHandler<TestSerializableData> dynamicErrorHandler =
          (e, p) -> errorHandler.handleError(e, p);
      streamService =
          factory.createRemoteStreamServer(
              cluster.getCommunicationService(),
              buffer -> {
                final var data = new TestSerializableData();
                data.wrap(buffer, 0, buffer.capacity());
                return data;
              },
              dynamicErrorHandler,
              RemoteStreamMetrics.noop());
    }

    private void start() {
      cluster.start().join();
      actorScheduler.submitActor(this).join();
      streamer = actor.call(() -> streamService.start(actorScheduler, this)).join().join();
    }

    private MemberId memberId() {
      return cluster.getMembershipService().getLocalMember().id();
    }

    @Override
    public ActorFuture<Void> closeAsync() {
      final ActorFuture<Void> closed = actor.createFuture();
      actor.run(
          () ->
              actor.runOnCompletion(
                  streamService.closeAsync(this),
                  (ok, error) -> cluster.stop().whenComplete(closed)));

      return closed;
    }
  }

  private final class TestClient implements AutoCloseable {
    private final AtomixCluster cluster;
    private final ClientStreamService<TestSerializableData> streamService;

    public TestClient(final AtomixCluster cluster) {
      this.cluster = cluster;

      final var factory = new TransportFactory(actorScheduler);
      streamService =
          factory.createRemoteStreamClient(
              cluster.getCommunicationService(), ClientStreamMetrics.noop());
    }

    private void start() {
      cluster.start().join();
      streamService.start(actorScheduler).join();
    }

    @Override
    public void close() {
      streamService.closeAsync().join();
      cluster.stop().join();
    }
  }
}
