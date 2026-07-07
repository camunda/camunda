/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.utils.net.Address;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/**
 * Tests binding the messaging service to an ephemeral port (0), where the OS assigns a free port
 * which must be resolved into the advertised address.
 */
final class NettyMessagingServiceEphemeralPortTest {
  @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();
  private final List<ManagedMessagingService> services = new ArrayList<>();

  @AfterEach
  void tearDown() {
    CloseHelper.quietCloseAll(services.stream().map(s -> (AutoCloseable) () -> s.stop().join()));
  }

  @Test
  void shouldResolveEphemeralPortIntoAdvertisedAddress() {
    // given
    final var service = createService(Address.from("127.0.0.1", 0));

    // when
    service.start().join();

    // then
    assertThat(service.address().port()).isPositive();
    assertThat(service.bindingAddresses())
        .isNotEmpty()
        .allSatisfy(address -> assertThat(address.port()).isEqualTo(service.address().port()));
  }

  @Test
  void shouldReceiveMessagesOnResolvedAddress() {
    // given
    final var receiver = createService(Address.from("127.0.0.1", 0));
    final var sender = createService(Address.from("127.0.0.1", 0));
    receiver.start().join();
    sender.start().join();
    receiver.registerHandler(
        "test", (sourceAddress, payload) -> CompletableFuture.completedFuture(payload));

    // when
    final var response =
        sender.sendAndReceive(
            receiver.address(), "test", "hello".getBytes(), true, Duration.ofSeconds(10));

    // then
    assertThat(response)
        .succeedsWithin(10, TimeUnit.SECONDS)
        .satisfies(payload -> assertThat(payload).containsExactly("hello".getBytes()));
  }

  @Test
  void shouldHandleSelfAddressedMessagesLocally() {
    // given
    final var service = createService(Address.from("127.0.0.1", 0));
    service.start().join();
    service.registerHandler(
        "test", (sourceAddress, payload) -> CompletableFuture.completedFuture(payload));

    // when - sending to the resolved advertised address must short-circuit to the local connection
    final var response =
        service.sendAndReceive(
            service.address(), "test", "self".getBytes(), true, Duration.ofSeconds(10));

    // then
    assertThat(response)
        .succeedsWithin(10, TimeUnit.SECONDS)
        .satisfies(payload -> assertThat(payload).containsExactly("self".getBytes()));
  }

  private ManagedMessagingService createService(final Address advertisedAddress) {
    final var service =
        new NettyMessagingService(
            "ephemeral-port-test", advertisedAddress, new MessagingConfig(), registry);
    services.add(service);
    return service;
  }
}
