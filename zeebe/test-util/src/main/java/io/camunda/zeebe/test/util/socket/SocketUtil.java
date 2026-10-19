/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.socket;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SocketUtil {
  static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.test.util.SocketUtil");

  private static final String DEFAULT_HOST = "localhost";
  // most unix based systems reserve ports from 0-1024; picking 1025 as our base port gives us
  // plenty of ports before we will collide with Docker's base port
  private static final int MIN_PORT = 1025;
  // Docker maps exposed ports to random local ports, starting at 32768; this is undocumented, but
  // observed empirically, and is aligned with Linux conventions, e.g. see
  // https://tldp.org/LDP/solrhe/Securing-Optimizing-Linux-RH-Edition-v1.3/chap6sec70.html
  private static final int MAX_PORT = 31025;

  private static final PortAllocator PORT_ALLOCATOR =
      new PortAllocator(
          Path.of(System.getProperty("java.io.tmpdir"), "zeebe-test-ports"), MIN_PORT, MAX_PORT);

  private SocketUtil() {}

  public static synchronized InetSocketAddress getNextAddress() {
    final int port = PORT_ALLOCATOR.nextPort();
    LOG.info("Choosing next port {}", port);
    return new InetSocketAddress(DEFAULT_HOST, port);
  }
}
