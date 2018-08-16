/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  public static final String DEFAULT_HOST = "localhost";
  public static final int BASE_PORT = 25600;
  public static final int RANGE_SIZE = 100;

  private static final int TEST_FORK_NUMBER;
  private static final PortRange PORT_RANGE;

  static {
    int testForkNumber = 0;
    try {
      final String testForkNumberProperty = System.getProperty("testForkNumber");
      if (testForkNumberProperty != null) {
        testForkNumber = Integer.valueOf(testForkNumberProperty);
      }
    } catch (Exception e) {
      LOG.warn("Failed to read test fork number system property");
    }

    final int min = BASE_PORT + testForkNumber * RANGE_SIZE;
    final int max = min + RANGE_SIZE;
    TEST_FORK_NUMBER = testForkNumber;
    PORT_RANGE = new PortRange(DEFAULT_HOST, min, max);
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

      LOG.debug("Choosing next port {} for test fork {}", next, TEST_FORK_NUMBER);
      return next;
    }
  }
}
