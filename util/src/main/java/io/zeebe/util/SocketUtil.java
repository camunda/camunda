/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

import java.net.InetSocketAddress;

public final class SocketUtil {
  private static final String HOST_PORT_FORMAT = "%s:%d";

  private SocketUtil() {}

  public static String toHostAndPortString(InetSocketAddress inetSocketAddress) {
    return String.format(
        HOST_PORT_FORMAT, inetSocketAddress.getHostString(), inetSocketAddress.getPort());
  }
}
