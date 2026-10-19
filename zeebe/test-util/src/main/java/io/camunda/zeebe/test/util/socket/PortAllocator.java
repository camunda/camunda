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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hands out TCP ports such that no two callers sharing the same lock directory get the same port,
 * even across JVMs. The OS cannot reserve a port for a component that binds it later, so
 * cooperating callers reserve ports by holding an exclusive file lock per port.
 *
 * <p>A reservation lasts until the JVM exits. The OS releases the file locks then, also if the JVM
 * crashes, so no stale reservations are left behind. The lock files themselves are never deleted,
 * as deleting one would let another process lock a new file under the same name.
 *
 * <p>Processes that do not use this class are only avoided if they already listen on the port when
 * it is chosen.
 */
final class PortAllocator {
  private final Path lockDir;
  private final int minPort;
  private final int portCount;
  private final Set<Integer> triedPorts = new HashSet<>();
  // keeps the channels, and thereby their locks, open until the JVM exits
  private final List<FileChannel> reservations = new ArrayList<>();

  private int cursor;

  /**
   * @param lockDir directory shared by all allocators that must not hand out the same port
   * @param minPort the lowest port to hand out
   * @param maxPort the upper bound of the ports to hand out, exclusive
   */
  PortAllocator(final Path lockDir, final int minPort, final int maxPort) {
    this.lockDir = lockDir;
    this.minPort = minPort;
    portCount = maxPort - minPort;
    // start at a random port to make it unlikely that concurrent JVMs probe the same ports
    cursor = ThreadLocalRandom.current().nextInt(portCount);
  }

  synchronized int nextPort() {
    try {
      Files.createDirectories(lockDir);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create port lock directory " + lockDir, e);
    }

    for (int i = 0; i < portCount; i++) {
      final int port = minPort + (cursor++ % portCount);
      if (triedPorts.add(port) && tryReserve(port)) {
        return port;
      }
    }

    throw new IllegalStateException(
        "No free port left in range [%d, %d)".formatted(minPort, minPort + portCount));
  }

  private boolean tryReserve(final int port) {
    final FileChannel channel;
    try {
      channel =
          FileChannel.open(
              lockDir.resolve(port + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to open lock file for port " + port, e);
    }

    try {
      if (channel.tryLock() != null && isBindable(port)) {
        reservations.add(channel);
        return true;
      }
    } catch (final OverlappingFileLockException e) {
      // another allocator in this JVM holds the port
    } catch (final IOException e) {
      closeQuietly(channel);
      throw new UncheckedIOException("Failed to lock port " + port, e);
    }

    closeQuietly(channel);
    return false;
  }

  private static boolean isBindable(final int port) {
    try (final var socket = new ServerSocket()) {
      socket.setReuseAddress(true);
      socket.bind(new InetSocketAddress(port));
      return true;
    } catch (final IOException e) {
      return false;
    }
  }

  private static void closeQuietly(final FileChannel channel) {
    try {
      channel.close();
    } catch (final IOException ignored) {
      // closing releases the lock if there was one, nothing else to do
    }
  }
}
