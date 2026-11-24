/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.atomix.cluster.messaging.UnicastService;
import io.atomix.utils.net.Address;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

final class CompositeUnicastServiceTest {

  private static final String SUBJECT = "test-subject";
  private static final Address ADDRESS = Address.from("127.0.0.1", 26502);

  private final UnicastService primary = mock(UnicastService.class);
  private final UnicastService secondaryReceiver = mock(UnicastService.class);
  private final CompositeUnicastService service =
      new CompositeUnicastService(primary, List.of(primary, secondaryReceiver));

  @Test
  void shouldSendOnlyOverThePrimary() {
    // given
    final var payload = "hello".getBytes();

    // when
    service.unicast(ADDRESS, SUBJECT, payload);

    // then - sending over every transport would double every gossip message
    verify(primary).unicast(ADDRESS, SUBJECT, payload);
    verifyNoInteractions(secondaryReceiver);
  }

  @Test
  void shouldListenOnEveryReceiver() {
    // given - a node must be reachable on all transports, so that the transport in use can be
    // switched one node at a time
    final BiConsumer<Address, byte[]> listener = (sender, payload) -> {};
    final Executor executor = Runnable::run;

    // when
    service.addListener(SUBJECT, listener, executor);

    // then
    verify(primary).addListener(SUBJECT, listener, executor);
    verify(secondaryReceiver).addListener(SUBJECT, listener, executor);
  }

  @Test
  void shouldRemoveListenersFromEveryReceiver() {
    // given
    final BiConsumer<Address, byte[]> listener = (sender, payload) -> {};

    // when
    service.removeListener(SUBJECT, listener);

    // then - a listener left behind on one transport would keep receiving after removal
    verify(primary).removeListener(SUBJECT, listener);
    verify(secondaryReceiver).removeListener(SUBJECT, listener);
  }
}
