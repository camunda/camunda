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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hands out TCP ports such that no two callers sharing the same lock directory get the same port,
 * even across JVMs. The OS cannot reserve a port for a component that binds it later, so
 * cooperating callers reserve ports by holding an exclusive lock on one byte of a shared lock file,
 * where the byte offset is the port number.
 *
 * <p>All reservations of an allocator share one file descriptor that stays open until the JVM
 * exits, so the number of reservations does not affect descriptor usage. A reservation lasts until
 * it is {@linkplain PortReservation#close() closed} or the JVM exits. The OS releases the locks
 * when the JVM exits, also if it crashes, so no stale reservations are left behind. Ports whose
 * {@link PortReservation} is dropped without closing it stay reserved until then.
 *
 * <p>Processes that do not use this class are only avoided if they already listen on the port when
 * it is chosen.
 */
final class PortAllocator {
  private final Path lockDir;
  private final int minPort;
  private final int portCount;

  private int cursor;
  // never closed: closing a channel drops all the locks this process holds on the file on some
  // platforms. The JVM rejects overlapping locks on the same file even across channels, so
  // allocators of this JVM that share a lock directory still exclude each other.
  private FileChannel channel;

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

  /** Reserves a port until the JVM exits. */
  int nextPort() {
    return reserve().port();
  }

  /** Reserves a port until the returned reservation is closed or the JVM exits. */
  synchronized PortReservation reserve() {
    if (channel == null) {
      channel = openChannel();
    }

    for (int i = 0; i < portCount; i++) {
      final int port = minPort + cursor;
      cursor = (cursor + 1) % portCount;
      final var reservation = tryReserve(port);
      if (reservation != null) {
        return reservation;
      }
    }

    throw new IllegalStateException(
        "No free port left in range [%d, %d)".formatted(minPort, minPort + portCount));
  }

  private PortReservation tryReserve(final int port) {
    final FileLock lock;
    try {
      lock = channel.tryLock(port, 1, false);
    } catch (final OverlappingFileLockException e) {
      // reserved by this JVM
      return null;
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to reserve port " + port, e);
    }
    if (lock == null) {
      // reserved by another process
      return null;
    }

    final var reservation = new PortReservation(port, lock);
    if (!isBindable(port)) {
      reservation.close();
      return null;
    }
    return reservation;
  }

  private FileChannel openChannel() {
    try {
      Files.createDirectories(lockDir);
      return FileChannel.open(
          lockDir.resolve("ports.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to open port lock file in " + lockDir, e);
    }
  }

  private static boolean isBindable(final int port) {
    return isBindable(new InetSocketAddress(port))
        && isBindable(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
  }

  private static boolean isBindable(final InetSocketAddress address) {
    try (final var socket = new ServerSocket()) {
      socket.setReuseAddress(true);
      socket.bind(address);
      return true;
    } catch (final IOException e) {
      return false;
    }
  }
}
