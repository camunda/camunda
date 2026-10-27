/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ModeTestUtils {

  private static boolean assertPortState(
      final String host, final int port, final boolean expectedOpen) {
    Socket socket = null;
    try {
      socket = new Socket();
      socket.connect(new InetSocketAddress(host, port), 1_000);
      return expectedOpen;
    } catch (IOException e) {
      return !expectedOpen;
    } finally {
      if (socket != null) {
        try {
          socket.close();
        } catch (IOException _e) {
          // ignore
        }
      }
    }
  }

  public static void assertPortOpen(final String host, final int port) {
    if (!assertPortState(host, port, true)) {
      throw new AssertionError("Expected port " + port + " to be open");
    }
  }
}
