/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.socket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileLock;

/**
 * A port that no other caller of {@link SocketUtil} gets while the reservation is held. It is held
 * until it is {@linkplain #close() closed} or the JVM exits.
 */
public final class PortReservation implements AutoCloseable {
  private final int port;
  private final FileLock lock;

  PortReservation(final int port, final FileLock lock) {
    this.port = port;
    this.lock = lock;
  }

  public int port() {
    return port;
  }

  /** Makes the port available to other callers again. Does nothing if already closed. */
  @Override
  public void close() {
    try {
      if (lock.isValid()) {
        lock.release();
      }
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to release port " + port, e);
    }
  }
}
