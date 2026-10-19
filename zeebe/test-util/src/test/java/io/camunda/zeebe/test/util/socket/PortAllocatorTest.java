/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PortAllocatorTest {
  // ports in the allocator under test are only reserved against other allocators sharing the same
  // lock directory, so each test gets its own directory and cannot interfere with the others
  private static final int MIN = 20_000;
  private static final int MAX = 21_000;

  @TempDir private Path lockDir;
  private final List<Process> children = new ArrayList<>();

  @AfterEach
  void tearDown() {
    children.forEach(Process::destroyForcibly);
  }

  @Test
  void shouldHandOutUniquePortsToConcurrentCallers() throws Exception {
    // given
    final var allocator = new PortAllocator(lockDir, MIN, MAX);
    final Set<Integer> ports = ConcurrentHashMap.newKeySet();
    final int threads = 8;
    final int portsPerThread = 50;
    final var start = new CountDownLatch(1);
    final var done = new CountDownLatch(threads);

    // when
    for (int t = 0; t < threads; t++) {
      new Thread(
              () -> {
                try {
                  start.await();
                  for (int i = 0; i < portsPerThread; i++) {
                    ports.add(allocator.nextPort());
                  }
                } catch (final InterruptedException e) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }
    start.countDown();

    // then
    assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(ports).hasSize(threads * portsPerThread);
  }

  @Test
  void shouldSkipPortsBoundByOtherProcesses() throws IOException {
    // given
    try (final var occupied = new ServerSocket()) {
      occupied.setReuseAddress(true);
      occupied.bind(new InetSocketAddress(MIN));
      final var allocator = new PortAllocator(lockDir, MIN, MIN + 2);

      // when
      final int port = allocator.nextPort();

      // then
      assertThat(port).isEqualTo(MIN + 1);
    }
  }

  @Test
  void shouldFailWhenNoPortIsLeft() {
    // given
    final var allocator = new PortAllocator(lockDir, MIN, MIN + 3);
    for (int i = 0; i < 3; i++) {
      allocator.nextPort();
    }

    // when - then
    assertThatThrownBy(allocator::nextPort).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldNeverHandOutTheSamePortToDifferentProcesses() throws Exception {
    // given - three JVMs that each hold their ports while the others allocate
    final int processes = 3;
    final int portsPerProcess = 100;

    // when
    final var readers = new ArrayList<BufferedReader>();
    for (int i = 0; i < processes; i++) {
      final var child = startChild(MIN, MAX, portsPerProcess);
      readers.add(new BufferedReader(new InputStreamReader(child.getInputStream())));
    }
    final Set<String> ports = new HashSet<>();
    for (final var reader : readers) {
      for (String line = reader.readLine(); !"done".equals(line); line = reader.readLine()) {
        assertThat(line).isNotNull();
        ports.add(line);
      }
    }

    // then
    assertThat(ports).hasSize(processes * portsPerProcess);
  }

  @Test
  void shouldFreePortsWhenTheOwningProcessDies() throws Exception {
    // given - another JVM holds every port in the range
    final var child = startChild(MIN, MIN + 3, 3);
    final var reader = new BufferedReader(new InputStreamReader(child.getInputStream()));
    for (String line = reader.readLine(); !"done".equals(line); line = reader.readLine()) {
      assertThat(line).isNotNull();
    }
    final var allocator = new PortAllocator(lockDir, MIN, MIN + 3);
    assertThatThrownBy(allocator::nextPort).isInstanceOf(IllegalStateException.class);

    // when
    child.destroyForcibly().waitFor();

    // then
    assertThatCode(allocator::nextPort).doesNotThrowAnyException();
  }

  @Test
  void shouldKeepPortsReservedWhenAnotherAllocatorInTheSameJvmProbesThem() throws Exception {
    // given
    final var first = new PortAllocator(lockDir, MIN, MIN + 3);
    final var second = new PortAllocator(lockDir, MIN, MIN + 3);
    final var ports = new HashSet<Integer>();
    ports.add(first.nextPort());
    ports.add(first.nextPort());
    ports.add(second.nextPort());

    // when - the second allocator probes the ports of the first one
    assertThatThrownBy(second::nextPort).isInstanceOf(IllegalStateException.class);

    // then
    assertThat(ports).hasSize(3);
    final var child = startChild(MIN, MIN + 3, 1);
    assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
    assertThat(child.exitValue()).isNotZero();
  }

  private Process startChild(final int min, final int max, final int count) throws IOException {
    final var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    final var child =
        new ProcessBuilder(
                java,
                "-cp",
                System.getProperty("java.class.path"),
                PortAllocatorMain.class.getName(),
                lockDir.toString(),
                String.valueOf(min),
                String.valueOf(max),
                String.valueOf(count))
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    children.add(child);
    return child;
  }
}
