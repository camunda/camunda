/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.utils.net.Address;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/** Tests binding the unicast service to an ephemeral port (0), resolved by the OS on bind. */
final class NettyUnicastServiceEphemeralPortTest {
  @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();
  private final List<NettyUnicastService> services = new ArrayList<>();

  @AfterEach
  void tearDown() {
    CloseHelper.quietCloseAll(services.stream().map(s -> (AutoCloseable) () -> s.stop().join()));
  }

  @Test
  void shouldResolveEphemeralPortOnBind() {
    // given
    final var service = createService(Address.from("127.0.0.1", 0));

    // when
    service.start().join();

    // then
    assertThat(service.address().port()).isPositive();
    assertThat(service.bindAddress().port()).isEqualTo(service.address().port());
  }

  @Test
  void shouldExchangeMessagesBetweenEphemeralPortServices() throws Exception {
    // given
    final var receiver = createService(Address.from("127.0.0.1", 0));
    final var sender = createService(Address.from("127.0.0.1", 0));
    receiver.start().join();
    sender.start().join();

    final var received = new CompletableFuture<byte[]>();
    receiver.addListener("test", (address, payload) -> received.complete(payload), Runnable::run);

    // when
    sender.unicast(receiver.address(), "test", "hello".getBytes());

    // then
    assertThat(received)
        .succeedsWithin(10, TimeUnit.SECONDS)
        .satisfies(payload -> assertThat(payload).containsExactly("hello".getBytes()));
  }

  private NettyUnicastService createService(final Address advertisedAddress) {
    final var service =
        new NettyUnicastService(
            "ephemeral-port-test", advertisedAddress, new MessagingConfig(), registry);
    services.add(service);
    return service;
  }
}
