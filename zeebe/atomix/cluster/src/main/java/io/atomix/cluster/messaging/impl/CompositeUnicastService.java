/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import io.atomix.cluster.messaging.UnicastService;
import io.atomix.utils.net.Address;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import org.jspecify.annotations.NullMarked;

/**
 * Routes unreliable unicast across several transports: one primary sends, and every receiver
 * listens.
 *
 * <p>The asymmetry is deliberate. A node sends over a single transport but is heard on all of them,
 * so the transport can be switched one node at a time — a node that has already switched is still
 * reachable from one that has not.
 *
 * <p>It has no lifecycle of its own, because it owns nothing: the transports it routes to are
 * started and stopped by whoever owns them. A {@code stop()} here could not stop delivery on a
 * transport that is still running, so there is no state to keep consistent.
 */
@NullMarked
final class CompositeUnicastService implements UnicastService {

  private final UnicastService primary;
  private final List<UnicastService> receivers;

  CompositeUnicastService(final UnicastService primary, final List<UnicastService> receivers) {
    this.primary = primary;
    this.receivers = List.copyOf(receivers);
  }

  @Override
  public void unicast(final Address address, final String subject, final byte[] message) {
    primary.unicast(address, subject, message);
  }

  @Override
  public void addListener(
      final String subject, final BiConsumer<Address, byte[]> listener, final Executor executor) {
    receivers.forEach(receiver -> receiver.addListener(subject, listener, executor));
  }

  @Override
  public void removeListener(final String subject, final BiConsumer<Address, byte[]> listener) {
    receivers.forEach(receiver -> receiver.removeListener(subject, listener));
  }
}
