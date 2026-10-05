/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The cluster is shared by every test because each node assigns a port; each test gets fresh
 * counters and collectors on it.
 */
final class ClusterLoadCollectorTest {

  private static final Duration WINDOW = Duration.ofMinutes(1);
  private static final List<MemberId> BROKERS =
      List.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2"));

  private static final List<AtomixCluster> NODES = new ArrayList<>();

  private final ControlledInstantSource clock =
      new ControlledInstantSource(Instant.parse("2026-01-01T00:00:00Z"));
  private final WindowExecutor coordinatorExecutor = new WindowExecutor();
  private final List<FakeCounters> counters = new ArrayList<>();
  private final List<ClusterLoadCollector> collectors = new ArrayList<>();

  @BeforeAll
  static void startCluster() {
    final var nodes =
        BROKERS.stream()
            .map(
                id ->
                    Node.builder()
                        .withId(id.id())
                        .withPort(SocketUtil.getNextAddress().getPort())
                        .build())
            .toList();
    nodes.forEach(
        node ->
            NODES.add(
                AtomixCluster.builder(new SimpleMeterRegistry())
                    .withMemberId(node.id().id())
                    .withAddress(node.address())
                    .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
                    .withMembershipProtocol(new DiscoveryMembershipProtocol())
                    .build()));
    CompletableFuture.allOf(
            NODES.stream().map(AtomixCluster::start).toArray(CompletableFuture[]::new))
        .join();
  }

  @AfterAll
  static void stopCluster() {
    CompletableFuture.allOf(
            NODES.stream().map(AtomixCluster::stop).toArray(CompletableFuture[]::new))
        .join();
    NODES.clear();
  }

  @BeforeEach
  void startCollectors() {
    for (int i = 0; i < BROKERS.size(); i++) {
      counters.add(new FakeCounters());
      collectors.add(startCollector(i));
    }
  }

  @AfterEach
  void stopCollectors() {
    collectors.forEach(ClusterLoadCollector::close);
  }

  @Test
  void shouldSumWhatEveryBrokerCountedOverTheWindow() {
    // given
    counters.forEach(c -> c.add(LoadMeasure.ROOT_PROCESS_INSTANCES, 1_000));
    final var load = collectors.getFirst().collect(BROKERS, WINDOW);
    final var endOfWindow = awaitWindowStart();

    // when
    for (int i = 0; i < BROKERS.size(); i++) {
      counters.get(i).add(LoadMeasure.ROOT_PROCESS_INSTANCES, 60L * (i + 1));
    }
    counters.get(1).add(LoadMeasure.PROCESSED_COMMANDS, 600);
    endWindow(endOfWindow);

    // then
    final var measured = await(load);
    assertThat(measured.isComplete()).isTrue();
    assertThat(measured.ratesPerSecond().get(LoadMeasure.ROOT_PROCESS_INSTANCES))
        .isCloseTo(6.0, within(1e-9));
    assertThat(measured.ratesPerSecond().get(LoadMeasure.PROCESSED_COMMANDS))
        .isCloseTo(10.0, within(1e-9));
  }

  @Test
  void shouldMarkABrokerThatDoesNotAnswerAsUnaccounted() {
    // given
    final var absent = MemberId.from("absent");
    final var members = new ArrayList<>(BROKERS);
    members.add(absent);
    final var load = collectors.getFirst().collect(members, WINDOW);
    final var endOfWindow = awaitWindowStart();

    // when
    endWindow(endOfWindow);

    // then
    assertThat(await(load).unaccounted()).containsExactly(absent);
  }

  @Test
  void shouldMarkABrokerThatRestartedDuringTheWindowAsUnaccounted() {
    // given
    final var load = collectors.getFirst().collect(BROKERS, WINDOW);
    final var endOfWindow = awaitWindowStart();

    // when
    collectors.get(2).close();
    counters.set(2, new FakeCounters());
    collectors.set(2, startCollector(2));
    endWindow(endOfWindow);

    // then
    assertThat(await(load).unaccounted()).containsExactly(BROKERS.get(2));
  }

  private ClusterLoadCollector startCollector(final int index) {
    final var collector =
        new ClusterLoadCollector(
            BROKERS.get(index),
            index == 0 ? coordinatorExecutor : new TestConcurrencyControl(),
            counters.get(index)::total,
            NODES.get(index).getCommunicationService(),
            clock);
    collector.start();
    return collector;
  }

  private Runnable awaitWindowStart() {
    return coordinatorExecutor.scheduled.orTimeout(10, TimeUnit.SECONDS).join();
  }

  private void endWindow(final Runnable endOfWindow) {
    clock.advance(WINDOW);
    endOfWindow.run();
  }

  private static ClusterLoad await(final ActorFuture<ClusterLoad> load) {
    return load.toCompletableFuture().orTimeout(10, TimeUnit.SECONDS).join();
  }

  /** Holds back the end of the window until the test releases it. */
  private static final class WindowExecutor extends TestConcurrencyControl {
    private final CompletableFuture<Runnable> scheduled = new CompletableFuture<>();

    @Override
    public ScheduledTimer schedule(final long delayMs, final Runnable runnable) {
      scheduled.complete(runnable);
      return () -> {};
    }
  }

  private static final class FakeCounters {
    private final Map<LoadMeasure, AtomicLong> totals = new EnumMap<>(LoadMeasure.class);

    private FakeCounters() {
      for (final var measure : LoadMeasure.values()) {
        totals.put(measure, new AtomicLong());
      }
    }

    private void add(final LoadMeasure measure, final long amount) {
      totals.get(measure).addAndGet(amount);
    }

    private long total(final LoadMeasure measure) {
      return totals.get(measure).get();
    }
  }
}
