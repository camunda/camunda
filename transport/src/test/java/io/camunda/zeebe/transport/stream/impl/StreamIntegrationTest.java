/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.transport.TransportFactory;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests end-to-end stream management from client to server */
class StreamIntegrationTest {

  private ClientStreamService<SerializableData> clientStreamer;
  private RemoteStreamer<SerializableData, SerializableData> remoteStreamer;

  private final DirectBuffer streamType = BufferUtil.wrapString("foo");
  private final SerializableData metadata = new SerializableData().data(1);
  private ActorScheduler actorScheduler;

  private final List<AutoCloseable> closeables = new ArrayList<>();

  @BeforeEach
  void setup() {
    startActorScheduler();

    // set up communication service
    final ConcurrentMap<MemberId, TestCommunicationService> cluster = new ConcurrentHashMap<>();
    final MemberId clientId = MemberId.from("client");
    final var clientService = new TestCommunicationService(cluster, clientId);
    final MemberId serverId = MemberId.from("server");
    final var serverService = new TestCommunicationService(cluster, serverId);
    cluster.put(clientId, clientService);
    cluster.put(serverId, serverService);

    // start server side streaming service
    remoteStreamer = startRemoteStreamer(serverService);

    // start client side streaming service
    clientStreamer = startClientStreamer(clientService, serverId);
  }

  private ClientStreamService<SerializableData> startClientStreamer(
      final TestCommunicationService clientService, final MemberId serverId) {
    final ClientStreamService<SerializableData> clientStreamService =
        new ClientStreamService<>(clientService);
    actorScheduler.submitActor(clientStreamService).join();
    closeables.add(clientStreamService);

    clientStreamService.onServerJoined(serverId);

    return clientStreamService;
  }

  private void startActorScheduler() {
    actorScheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .build();
    actorScheduler.start();
    closeables.add(() -> actorScheduler.stop());
  }

  private RemoteStreamer<SerializableData, SerializableData> startRemoteStreamer(
      final TestCommunicationService serverService) {
    // required to start and stop remote stream service
    final TestActor testActor = new TestActor();
    actorScheduler.submitActor(testActor).join();
    closeables.add(testActor);

    return testActor
        .call(
            () -> {
              final RemoteStreamService<SerializableData, SerializableData> remoteStreamService =
                  new TransportFactory(actorScheduler)
                      .createRemoteStreamServer(
                          serverService, SerializableData::new, RemoteStreamMetrics.noop());
              closeables.add(
                  () -> testActor.call(() -> remoteStreamService.closeAsync(testActor)).join());
              return remoteStreamService.start(actorScheduler, testActor);
            })
        .join()
        .join();
  }

  @AfterEach
  void tearDown() {
    Collections.reverse(closeables);
    closeables.forEach(
        c -> {
          try {
            c.close();
          } catch (final Exception e) {
            throw new RuntimeException(e);
          }
        });
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
              final SerializableData payload = new SerializableData();
              payload.wrap(p, 0, p.capacity());
              payloads.get().add(payload.data());
              latch.countDown();
            })
        .join();

    // when
    Awaitility.await().until(() -> remoteStreamer.streamFor(streamType).isPresent());

    pushPayload(new SerializableData().data(100));
    pushPayload(new SerializableData().data(200));

    // then
    // verify client receives payload
    latch.await();
    assertThat(payloads.get()).asList().containsExactly(100, 200);
  }

  @Test
  void shouldReturnErrorWhenClientStreamIsClosed() throws InterruptedException {
    // given
    final var clientStreamId = clientStreamer.add(streamType, metadata, p -> {}).join();
    Awaitility.await().until(() -> remoteStreamer.streamFor(streamType).isPresent());
    final var serverStream = remoteStreamer.streamFor(streamType).orElseThrow();

    // when
    clientStreamer.remove(clientStreamId);

    final AtomicReference<Throwable> error = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);
    // Use serverStream obtained before stream is removed
    serverStream.push(
        new SerializableData().data(100),
        (e, p) -> {
          error.set(e);
          latch.countDown();
        });

    // then
    latch.await();
    assertThat(error.get()).hasCauseInstanceOf(StreamDoesNotExistException.class);
  }

  private void pushPayload(final SerializableData data) {
    remoteStreamer.streamFor(streamType).orElseThrow().push(data, (p, e) -> {});
  }

  private static class SerializableData implements BufferReader, BufferWriter {

    private int data;

    public int data() {
      return data;
    }

    public SerializableData data(final int data) {
      this.data = data;
      return this;
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      data = buffer.getInt(0);
    }

    @Override
    public int getLength() {
      return Integer.BYTES;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putInt(offset, data);
    }
  }

  private static final class TestActor extends Actor {}
}
