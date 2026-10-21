/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.socket;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Entry point for {@link PortAllocatorTest} to allocate ports from a separate JVM. Prints each
 * allocated port on its own line, then keeps holding the ports until its stdin is closed.
 *
 * <p>Arguments: lock directory, min port, max port, number of ports to allocate.
 */
public final class PortAllocatorMain {
  private PortAllocatorMain() {}

  public static void main(final String[] args) throws IOException {
    final var allocator =
        new PortAllocator(Path.of(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
    final int count = Integer.parseInt(args[3]);
    for (int i = 0; i < count; i++) {
      System.out.println(allocator.nextPort());
    }
    System.out.println("done");
    System.out.flush();
    //noinspection StatementWithEmptyBody
    while (System.in.read() != -1) {}
  }
}
