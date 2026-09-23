/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Iterator;

/**
 * Allocates OS-assigned ephemeral ports.
 *
 * <p>Used by Gradle test workers, whose global worker IDs cannot be mapped to slots in {@link
 * PortRange}'s bounded per-fork layout.
 */
class EphemeralPortRange implements Iterator<InetSocketAddress> {
  private final String host;
  private final int forkNumber;

  EphemeralPortRange(final String host, final int forkNumber) {
    this.host = host;
    this.forkNumber = forkNumber;
  }

  @Override
  public boolean hasNext() {
    return true;
  }

  @Override
  @SuppressWarnings("squid:S2272")
  public InetSocketAddress next() {
    // no need to throw NoSuchElementException since it never runs out of elements
    final int port = ephemeralPort();
    SocketUtil.LOG.info("Choosing next ephemeral port {} for test fork {}", port, forkNumber);
    return new InetSocketAddress(host, port);
  }

  private int ephemeralPort() {
    try (final ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (final IOException e) {
      throw new IllegalStateException("Unable to allocate an ephemeral test port", e);
    }
  }

  @Override
  public String toString() {
    return "EphemeralPortRange{" + "host='" + host + '\'' + ", forkNumber=" + forkNumber + '}';
  }
}
