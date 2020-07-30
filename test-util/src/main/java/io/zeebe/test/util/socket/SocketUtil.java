/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.socket;

import io.zeebe.test.util.TestEnvironment;
import io.zeebe.util.ZbLogger;
import java.net.InetSocketAddress;
import org.slf4j.Logger;

public final class SocketUtil {
  static final Logger LOG = new ZbLogger("io.zeebe.test.util.SocketUtil");

  private static final String DEFAULT_HOST = "localhost";
  private static final int BASE_PORT = 25600;
  private static final int RANGE_SIZE = 100;

  private static final int TEST_FORK_NUMBER;
  private static final PortRange PORT_RANGE;

  static {
    final int testForkNumber = TestEnvironment.getTestForkNumber();
    final int testMavenId = TestEnvironment.getTestMavenId();

    LOG.info(
        "Starting socket assignment with testForkNumber {} and testMavenId {}",
        testForkNumber,
        testMavenId);

    // ensure limits to stay in available port range
    assert testForkNumber < 39 : "System property test fork number has to be smaller then 39";
    assert testMavenId < 10 : "System property test maven id has to be smaller then 10";

    final int testOffset = testForkNumber * 10 + testMavenId;

    final int min = BASE_PORT + testOffset * RANGE_SIZE;
    final int max = min + RANGE_SIZE;
    TEST_FORK_NUMBER = testForkNumber;
    PORT_RANGE = new PortRange(DEFAULT_HOST, TEST_FORK_NUMBER, min, max);
  }

  private SocketUtil() {}

  public static synchronized InetSocketAddress getNextAddress() {
    return PORT_RANGE.next();
  }
}
