/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Iterator;

class PortRange implements Iterator<InetSocketAddress> {
  private final String host;
  private final int basePort;
  private final int maxOffset;
  private final int forkNumber;

  private int currentOffset;

  PortRange(final String host, final int forkNumber, final int min, final int max) {
    assert max <= 65535 : "Port range exceeds maximal available port 65535, got max port " + max;
    this.host = host;
    basePort = min;
    maxOffset = max - min;
    this.forkNumber = forkNumber;

    currentOffset = 0;
  }

  @Override
  public boolean hasNext() {
    return true;
  }

  @Override
  @SuppressWarnings("squid:S2272")
  public InetSocketAddress next() {
    // no need to throw NoSuchElementException since it never runs out of elements
    return new InetSocketAddress(host, nextPort());
  }

  private int nextPort() {
    int next;
    do {
      next = basePort + (currentOffset++ % maxOffset);
    } while (!portAvailable(next));

    SocketUtil.LOG.info(
        "Choosing next port {} for test fork {} with range {}", next, forkNumber, this);
    return next;
  }

  private boolean portAvailable(final int port) {
    try (final ServerSocket ss = new ServerSocket(port)) {
      ss.setReuseAddress(true);
      return true;
    } catch (final IOException ignored) { // NOSONAR
      /* should not be thrown */
    }

    return false;
  }

  @Override
  public String toString() {
    return "PortRange{"
        + "host='"
        + host
        + '\''
        + ", basePort="
        + basePort
        + ", maxOffset="
        + maxOffset
        + ", currentOffset="
        + currentOffset
        + '}';
  }
}
