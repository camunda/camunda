/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.ManagedUnicastService;
import io.atomix.cluster.messaging.UnicastService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
}
