/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.agrona.CloseHelper;

/**
 * Collects the ports reserved for one owner, e.g. a test application, so that they can be handed
 * out again together by {@linkplain #close() closing} it. Ports of an owner that is never closed
 * stay reserved until the JVM exits.
 */
public final class PortReservations implements AutoCloseable {
  private final Supplier<PortReservation> source;
  private final List<PortReservation> reservations = new ArrayList<>();

  public PortReservations() {
    this(SocketUtil::reserveNextPort);
  }

  PortReservations(final Supplier<PortReservation> source) {
    this.source = source;
  }

  /** Reserves a free port until this is closed. */
  public synchronized int nextPort() {
    final var reservation = source.get();
    reservations.add(reservation);
    return reservation.port();
  }

  /** Makes all ports reserved through this available to other callers again. */
  @Override
  public synchronized void close() {
    try {
      CloseHelper.closeAll(reservations);
    } finally {
      reservations.clear();
    }
  }
}
