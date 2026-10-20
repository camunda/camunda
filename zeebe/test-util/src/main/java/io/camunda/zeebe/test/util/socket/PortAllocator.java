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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.agrona.CloseHelper;

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
  // Lock files claimed by any allocator in this JVM. Opening and then closing a second channel
  // for a lock file this JVM already holds can release the OS lock on some platforms, so such files
  // must not be opened at all.
  private static final Set<Path> CLAIMED_LOCK_FILES = ConcurrentHashMap.newKeySet();

  private final Path lockDir;
  private final int minPort;
  private final int portCount;
  // keeps the channels, and thereby their locks, open until the JVM exits
  private final List<FileChannel> reservations = new ArrayList<>();

  private int cursor;
  private boolean lockDirCreated;

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
    if (!lockDirCreated) {
      try {
        Files.createDirectories(lockDir);
      } catch (final IOException e) {
        throw new UncheckedIOException("Failed to create port lock directory " + lockDir, e);
      }
      lockDirCreated = true;
    }

    for (int i = 0; i < portCount; i++) {
      final int port = minPort + cursor;
      cursor = (cursor + 1) % portCount;
      if (tryReserve(port)) {
        return port;
      }
    }

    throw new IllegalStateException(
        "No free port left in range [%d, %d)".formatted(minPort, minPort + portCount));
  }

  private boolean tryReserve(final int port) {
    final var lockFile = lockDir.resolve(port + ".lock").toAbsolutePath().normalize();
    // claiming the file keeps other allocators in this JVM away while we probe it; the file I/O and
    // the bind check happen outside the monitor so that probing does not block them
    if (!CLAIMED_LOCK_FILES.add(lockFile)) {
      return false;
    }

    boolean reserved = false;
    FileChannel channel = null;
    try {
      channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      if (channel.tryLock() != null && isBindable(port)) {
        reservations.add(channel);
        reserved = true;
      }
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to reserve port " + port, e);
    } finally {
      if (!reserved) {
        CloseHelper.quietClose(channel);
        CLAIMED_LOCK_FILES.remove(lockFile);
      }
    }
    return reserved;
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
}
