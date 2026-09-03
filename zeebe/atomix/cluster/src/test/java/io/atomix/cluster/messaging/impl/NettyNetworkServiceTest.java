/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.ManagedNetworkService;
import io.atomix.cluster.messaging.ManagedUnicastService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.UnicastService;
import io.atomix.utils.net.Address;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class NettyNetworkServiceTest {

  private final AtomicBoolean messagingStarted = new AtomicBoolean();
  private final AtomicBoolean messagingStopped = new AtomicBoolean();
  private final AtomicBoolean unicastStarted = new AtomicBoolean();
  private final AtomicBoolean unicastStopped = new AtomicBoolean();

  private final ManagedMessagingService messagingService = mock(ManagedMessagingService.class);
  private final ManagedUnicastService unicastService = mock(ManagedUnicastService.class);
  private final UnicastService routedUnicastService = mock(UnicastService.class);
  private final NettyNetworkService networkService =
      new NettyNetworkService(
          messagingService, unicastService, routedUnicastService, Runnable::run);

  NettyNetworkServiceTest() {
    when(messagingService.start())
        .thenAnswer(
            invocation -> {
              messagingStarted.set(true);
              return CompletableFuture.completedFuture(messagingService);
            });
    when(messagingService.stop())
        .thenAnswer(
            invocation -> {
              messagingStopped.set(true);
              return CompletableFuture.completedFuture(null);
            });
    when(unicastService.start())
        .thenAnswer(
            invocation -> {
              unicastStarted.set(true);
              return CompletableFuture.completedFuture(unicastService);
            });
    when(unicastService.stop())
        .thenAnswer(
            invocation -> {
              unicastStopped.set(true);
              return CompletableFuture.completedFuture(null);
            });
  }

  @Test
  void shouldStartBothTransports() {
    // when
    final var started = networkService.start().join();

    // then
    assertThat(messagingStarted).isTrue();
    assertThat(unicastStarted).isTrue();
    assertThat(started).isSameAs(networkService);
  }

  @Test
  void shouldStopBothTransports() {
    // when
    networkService.stop().join();

    // then
    assertThat(messagingStopped).isTrue();
    assertThat(unicastStopped).isTrue();
  }

  @Test
  void shouldStopMessagingWhenUnicastStopFails() {
    // given
    when(unicastService.stop())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("unicast stop failed")));

    // when
    final var stopped = networkService.stop();

    // then - leaving the messaging transport running would keep the node reachable after shutdown
    assertThat(stopped).succeedsWithin(Duration.ofSeconds(5));
    assertThat(messagingStopped).isTrue();
  }

  @Test
  void shouldExposeBothTransports() {
    // when - then
    assertThat(networkService.messagingService()).isSameAs(messagingService);
    assertThat(networkService.unicastService()).isSameAs(routedUnicastService);
  }

  /**
   * Binds real sockets, because the requirement is about what the operating system sees: with UDP
   * disabled, no datagram socket may exist. Receiving gossip in the clear is as disqualifying as
   * sending it, so "does not send over UDP" would not be enough.
   */
  @Nested
  final class TransportSelectionTest {

    @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();

    private ManagedNetworkService service;

    @AfterEach
    void tearDown() {
      if (service != null) {
        CloseHelper.quietClose(() -> service.stop().join());
      }
    }

    @Test
    void shouldNotBindADatagramSocketWhenUdpIsDisabled() throws Exception {
      // given
      final var address = Address.from("127.0.0.1", SocketUtil.getNextAddress().getPort());
      service = networkService(address, new MessagingConfig().setUdpEnabled(false));

      // when
      service.start().join();

      // then - nothing is listening on UDP at the internal API port
      try (final var probe = nonSharingDatagramSocket()) {
        assertThatCode(() -> probe.bind(new InetSocketAddress(address.host(), address.port())))
            .doesNotThrowAnyException();
      }
    }

    @Test
    void shouldBindADatagramSocketWhenUdpIsEnabled() throws Exception {
      // given
      final var address = Address.from("127.0.0.1", SocketUtil.getNextAddress().getPort());
      service = networkService(address, new MessagingConfig().setUdpEnabled(true));

      // when
      service.start().join();

      // then
      try (final var probe = nonSharingDatagramSocket()) {
        assertThatCode(() -> probe.bind(new InetSocketAddress(address.host(), address.port())))
            .isInstanceOf(Exception.class);
      }
    }

    private ManagedNetworkService networkService(
        final Address address, final MessagingConfig config) {
      return new NettyNetworkService(
          "zeebe", address, config, "transport-selection", registry, Runnable::run);
    }

    /**
     * An unbound socket with {@code SO_REUSEADDR} off, so that a successful bind means the port is
     * genuinely free rather than shared. Java's default for the option is platform-dependent, so it
     * is set explicitly.
     */
    private DatagramSocket nonSharingDatagramSocket() throws Exception {
      final var socket = new DatagramSocket(null);
      socket.setReuseAddress(false);
      return socket;
    }
  }
}
