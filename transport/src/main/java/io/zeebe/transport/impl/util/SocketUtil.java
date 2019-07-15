/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.util;

import io.zeebe.transport.SocketAddress;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Iterator;
import org.slf4j.Logger;

public class SocketUtil {

  public static final Logger LOG = new ZbLogger("io.zeebe.transport.impl.util.SocketUtil");

  public static final String TEST_FORK_NUMBER_PROPERTY_NAME = "testForkNumber";
  public static final String TEST_MAVEN_ID_PROPERTY_NAME = "testMavenId";

  public static final String DEFAULT_HOST = "localhost";
  public static final int BASE_PORT = 25600;
  public static final int RANGE_SIZE = 100;

  private static final int TEST_FORK_NUMBER;
  private static final PortRange PORT_RANGE;

  static {
    final int testForkNumber = getTestForkNumber();
    final int testMavenId = getTestMavenId();

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
    PORT_RANGE = new PortRange(DEFAULT_HOST, min, max);
  }

  private static int getTestForkNumber() {
    int testForkNumber = 0;
    try {
      final String testForkNumberProperty = System.getProperty(TEST_FORK_NUMBER_PROPERTY_NAME);
      if (testForkNumberProperty != null) {
        testForkNumber = Integer.valueOf(testForkNumberProperty);
      } else {
        LOG.warn(
            "No system property '{}' set, using default value {}",
            TEST_FORK_NUMBER_PROPERTY_NAME,
            testForkNumber);
      }
    } catch (Exception e) {
      LOG.warn("Failed to read test fork number system property", e);
    }
    return testForkNumber;
  }

  private static int getTestMavenId() {
    int testMavenId = 0;
    try {
      final String testMavenIdProperty = System.getProperty(TEST_MAVEN_ID_PROPERTY_NAME);
      if (testMavenIdProperty != null) {
        testMavenId = Integer.valueOf(testMavenIdProperty);
      } else {
        LOG.warn(
            "No system property '{}' set, using default value {}",
            TEST_MAVEN_ID_PROPERTY_NAME,
            testMavenId);
      }
    } catch (Exception e) {
      LOG.warn("Failed to read test maven id system property", e);
    }
    return testMavenId;
  }

  public static SocketAddress getNextAddress() {
    return PORT_RANGE.next();
  }

  private static boolean portAvailable(int port) {
    try (ServerSocket ss = new ServerSocket(port)) {
      ss.setReuseAddress(true);
      return true;
    } catch (IOException e) {
      /* should not be thrown */
    }

    return false;
  }

  static class PortRange implements Iterator<SocketAddress> {

    final String host;
    final int basePort;
    final int maxOffset;

    int currentOffset;

    PortRange(String host, int min, int max) {
      assert max <= 65535 : "Port range exceeds maximal available port 65535, got max port " + max;
      this.host = host;
      this.basePort = min;
      this.maxOffset = max - min;
      this.currentOffset = 0;
    }

    @Override
    public boolean hasNext() {
      return true;
    }

    @Override
    public SocketAddress next() {
      return new SocketAddress(host, nextPort());
    }

    private int nextPort() {
      int next;
      do {
        next = basePort + (currentOffset++ % maxOffset);
      } while (!portAvailable(next));

      LOG.info(
          "Choosing next port {} for test fork {} with range {}", next, TEST_FORK_NUMBER, this);
      return next;
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
}
