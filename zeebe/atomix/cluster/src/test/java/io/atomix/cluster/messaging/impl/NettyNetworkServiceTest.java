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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.ManagedUnicastService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class NettyNetworkServiceTest {

  private final ManagedMessagingService messagingService = mock(ManagedMessagingService.class);
  private final ManagedUnicastService unicastService = mock(ManagedUnicastService.class);
  private final NettyNetworkService networkService =
      new NettyNetworkService(messagingService, unicastService, Runnable::run);

  @Test
  void shouldStartBothTransports() {
    // given
    when(messagingService.start()).thenReturn(CompletableFuture.completedFuture(messagingService));
    when(unicastService.start()).thenReturn(CompletableFuture.completedFuture(unicastService));

    // when
    final var started = networkService.start().join();

    // then
    verify(messagingService).start();
    verify(unicastService).start();
    assertThat(started).isSameAs(networkService);
  }

  @Test
  void shouldStopBothTransports() {
    // given
    when(unicastService.stop()).thenReturn(CompletableFuture.completedFuture(null));
    when(messagingService.stop()).thenReturn(CompletableFuture.completedFuture(null));

    // when
    networkService.stop().join();

    // then
    verify(unicastService).stop();
    verify(messagingService).stop();
  }

  @Test
  void shouldStopMessagingWhenUnicastStopFails() {
    // given
    when(unicastService.stop())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("unicast stop failed")));
    when(messagingService.stop()).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var stopped = networkService.stop();

    // then - leaving the messaging transport running would keep the node reachable after shutdown
    assertThat(stopped).succeedsWithin(Duration.ofSeconds(5));
    verify(messagingService).stop();
  }

  @Test
  void shouldExposeBothTransports() {
    // when - then
    assertThat(networkService.messagingService()).isSameAs(messagingService);
    assertThat(networkService.unicastService()).isSameAs(unicastService);
  }
}
