/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import static io.camunda.zeebe.eventplane.service.Fixtures.HISTORY;
import static io.camunda.zeebe.eventplane.service.Fixtures.notice;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.eventplane.store.FileSystemStore;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PeerForwardingTest {

  private static final Duration SHORT = Duration.ofMillis(300);

  @TempDir private Path directory;

  @Test
  void shouldForwardANoticeToEveryPeerWithTheForwardedHeader() throws Exception {
    // given
    try (final var peer = new StubPeer();
        final var otherPeer = new StubPeer();
        final var service =
            ServiceFixture.start(
                new FileSystemStore(directory),
                config -> config.withPeers(List.of(peer.uri(), otherPeer.uri())))) {
      final var notice = notice("c", "default", 3, HISTORY, 1, 40);
      final var json = new String(notice.toJson(), StandardCharsets.UTF_8);

      // when
      service.post(notice);

      // then
      for (final var received : List.of(peer, otherPeer)) {
        final var request = received.next(SseStream.TIMEOUT);
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/uploads");
        assertThat(request.forwarded()).isEqualTo("true");
        assertThat(request.body()).isEqualTo(json);
      }
    }
  }

  @Test
  void shouldForwardToAPeerThatIsGivenWithATrailingSlash() throws Exception {
    // given
    try (final var peer = new StubPeer();
        final var service =
            ServiceFixture.start(
                new FileSystemStore(directory),
                config -> config.withPeers(List.of(URI.create(peer.uri() + "/"))))) {

      // when
      service.post(notice("c", "default", 3, HISTORY, 1, 40));

      // then
      final var request = peer.next(SseStream.TIMEOUT);
      assertThat(request).isNotNull();
      assertThat(request.path()).isEqualTo("/v1/uploads");
    }
  }

  @Test
  void shouldNotForwardANoticeThatAPeerForwarded() throws Exception {
    // given
    try (final var peer = new StubPeer();
        final var service =
            ServiceFixture.start(
                new FileSystemStore(directory), config -> config.withPeers(List.of(peer.uri())))) {
      final var forwarded = notice("c", "default", 3, HISTORY, 1, 40);
      final var own = notice("c", "default", 3, HISTORY, 41, 50);
      final var stream = service.stream("c/default/3");

      // when the forwarded notice is posted before one that came from a publisher
      final var response =
          service.post(
              "/v1/uploads",
              new String(forwarded.toJson(), StandardCharsets.UTF_8),
              PeerForwarder.FORWARDED_HEADER,
              "true");
      service.post(own);

      // then the stream has both, and the peer only the second
      assertThat(response.statusCode()).isEqualTo(202);
      assertThat(stream.nextNotice()).isEqualTo(forwarded);
      assertThat(stream.nextNotice()).isEqualTo(own);
      final var request = peer.next(SseStream.TIMEOUT);
      assertThat(request).isNotNull();
      assertThat(request.body()).isEqualTo(new String(own.toJson(), StandardCharsets.UTF_8));
      assertThat(peer.next(SHORT)).isNull();
    }
  }

  @Test
  void shouldPassANoticeOnBetweenTwoInstancesThatAreEachOthersPeersWithoutLooping()
      throws Exception {
    // given two instances that name each other, one on a port that is free for now
    final int portOfFirst = freePort();
    try (final var second =
            ServiceFixture.start(
                new FileSystemStore(directory.resolve("second")),
                config -> config.withPeers(List.of(peerAt(portOfFirst))));
        final var first =
            ServiceFixture.start(
                new FileSystemStore(directory.resolve("first")),
                portOfFirst,
                config -> config.withPeers(List.of(second.uri(""))))) {
      final var streamOfFirst = first.stream("c/default/3");
      final var streamOfSecond = second.stream("c/default/3");
      final var one = notice("c", "default", 3, HISTORY, 1, 40);
      final var two = notice("c", "default", 3, HISTORY, 41, 50);

      // when a notice is posted to each of them
      first.post(one);
      second.post(two);

      // then both streams have both notices once, and nothing comes back around
      assertThat(List.of(streamOfFirst.nextNotice(), streamOfFirst.nextNotice()))
          .containsExactlyInAnyOrder(one, two);
      assertThat(List.of(streamOfSecond.nextNotice(), streamOfSecond.nextNotice()))
          .containsExactlyInAnyOrder(one, two);
      assertThat(noticesWithin(streamOfFirst)).isZero();
      assertThat(noticesWithin(streamOfSecond)).isZero();
    }
  }

  @Test
  void shouldAnswerAndDeliverLocallyWhenAPeerIsDown() throws Exception {
    // given a peer that nobody listens at
    final var peer = peerAt(freePort());
    try (final var service =
        ServiceFixture.start(
            new FileSystemStore(directory), config -> config.withPeers(List.of(peer)))) {
      final var stream = service.stream("c/default/3");
      final var notice = notice("c", "default", 3, HISTORY, 1, 40);

      // when
      service.post(notice);

      // then
      assertThat(stream.nextNotice()).isEqualTo(notice);
    }
  }

  private static int noticesWithin(final SseStream stream) throws Exception {
    int notices = 0;
    final long deadline = System.nanoTime() + SHORT.toNanos();
    while (System.nanoTime() < deadline) {
      final var frame = stream.nextFrame(Duration.ofNanos(deadline - System.nanoTime()));
      if (frame != null && !frame.getFirst().startsWith(":")) {
        notices++;
      }
    }
    return notices;
  }

  private static URI peerAt(final int port) {
    return URI.create("http://127.0.0.1:" + port);
  }

  private static int freePort() throws IOException {
    try (final var socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
      return socket.getLocalPort();
    }
  }
}
