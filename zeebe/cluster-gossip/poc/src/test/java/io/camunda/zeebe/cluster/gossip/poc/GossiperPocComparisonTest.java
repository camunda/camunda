/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc;

import static io.camunda.zeebe.cluster.gossip.poc.TestCluster.memberId;

import io.camunda.zeebe.cluster.gossip.GlobalKey;
import io.camunda.zeebe.cluster.gossip.GossipCodec;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.MemberKey;
import io.camunda.zeebe.cluster.gossip.MergeStrategy;
import io.camunda.zeebe.cluster.gossip.poc.TestCluster.Timing;
import io.camunda.zeebe.cluster.gossip.poc.TestCluster.Transport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Measures the two proofs of concept side by side, to inform the choice of transport. Not a
 * regression test: it only reports. Run it explicitly:
 *
 * <pre>
 * ./mvnw verify -pl zeebe/cluster-gossip/poc -Dquickly -DskipTests=false \
 *   -Dtest=GossiperPocComparisonTest -Dgossip.poc.compare=true \
 *   -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * The report is printed and written to {@code target/gossip-poc-comparison.md}.
 */
@EnabledIfSystemProperty(named = "gossip.poc.compare", matches = "true")
final class GossiperPocComparisonTest {

  private static final int CLUSTER_SIZE = 5;
  private static final int SAMPLES = 10;
  private static final int[] BACKGROUND_SIZES = {0, 10_000, 100_000, 1_000_000};
  private static final int[] VALUE_SIZES = {
    1_000, 16_000, 40_000, 48_000, 64_000, 256_000, 1_000_000
  };

  private static final GossipCodec<String> STRING =
      new GossipCodec<>() {
        @Override
        public byte[] encode(final String value) {
          return value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String decode(final byte[] bytes) {
          return new String(bytes, StandardCharsets.UTF_8);
        }
      };

  private final StringBuilder report = new StringBuilder();

  @Test
  void compare() throws IOException {
    report.append("# Gossiper proof-of-concept comparison\n\n");
    report.append(
        String.format(
            Locale.ROOT,
            "Cluster of %d members on localhost; latency and traffic use SWIM's production timing"
                + " (gossip 250 ms, fanout 2, sync 10 s), the size limits use fast timing.%n%n",
            CLUSTER_SIZE));

    report.append("## Convergence latency and traffic per update\n\n");
    report.append(
        "| Transport | GLOBAL p50 / max (ms) | MEMBER p50 / max (ms) | Messages per update |"
            + " Bytes sent per update |\n");
    report.append("|---|---|---|---|---|\n");
    for (final var transport : Transport.values()) {
      measureLatencyAndTraffic(transport);
    }

    report.append("\n## Background TCP traffic of the cluster with nothing changing\n\n");
    report.append(
        "Bytes per second of TCP requests sent by all members together, from Atomix messaging"
            + " metrics. Responses are not recorded by Atomix (for SWIM probes they carry one more"
            + " member state), and SWIM's UDP gossip is not observable, so these are lower"
            + " bounds.\n\n");
    report.append(
        "| Transport | GLOBAL value size (bytes) | SWIM probes + syncs (B/s) | Gossiper transport"
            + " (B/s) | Total (B/s) |\n|---|---|---|---|---|\n");
    for (final int size : BACKGROUND_SIZES) {
      for (final var transport : Transport.values()) {
        measureBackgroundTraffic(transport, size);
      }
    }

    report.append("\n## Largest value that converges (GLOBAL key, 3 members, 15 s timeout)\n\n");
    report.append("| Value size (bytes) |");
    for (final var transport : Transport.values()) {
      report.append(' ').append(transport).append(" |");
    }
    report.append("\n|---|---|---|\n");
    for (final int size : VALUE_SIZES) {
      report.append("| ").append(size).append(" |");
      for (final var transport : Transport.values()) {
        report.append(' ').append(measureValueSize(transport, size)).append(" |");
      }
      report.append('\n');
    }

    report.append("\n## Time until a stopped member's MEMBER value is dropped elsewhere\n\n");
    report.append(
        "| Transport | GLOBAL state (bytes) | Drop after stop (ms) | Drop after restart (ms) |\n"
            + "|---|---|---|---|\n");
    for (final int stateSize : new int[] {0, 100_000}) {
      for (final var transport : Transport.values()) {
        measureLeave(transport, stateSize);
      }
    }

    System.out.println(report);
    final var output = Path.of("target", "gossip-poc-comparison.md");
    Files.createDirectories(output.getParent());
    Files.writeString(output, report);
  }

  private void measureLatencyAndTraffic(final Transport transport) {
    final GlobalKey<String> global =
        GossipKey.global("compare.global", STRING, MergeStrategy.lastWriterWins(), "");
    final MemberKey<String> member = GossipKey.member("compare.member", STRING);
    try (final var cluster = new TestCluster(transport, Timing.defaults(), CLUSTER_SIZE).start()) {
      awaitMembership(cluster);
      cluster.running().forEach(gossiper -> gossiper.get(global));

      final var globalLatencies = new ArrayList<Long>();
      final var memberLatencies = new ArrayList<Long>();
      long messages = 0;
      long bytes = 0;
      for (int i = 0; i < SAMPLES; i++) {
        final var writer = cluster.gossiper(i % CLUSTER_SIZE);
        final var value = "value-" + i;

        final long messagesBefore = totalMessages(cluster);
        final long bytesBefore = totalBytes(cluster);
        final long start = System.nanoTime();
        writer.put(global, value);
        awaitCondition(
            () -> cluster.running().stream().allMatch(g -> value.equals(g.get(global))),
            Duration.ofSeconds(30));
        globalLatencies.add(Duration.ofNanos(System.nanoTime() - start).toMillis());
        // let the rumor die out before counting what it cost
        sleepQuietly(Duration.ofSeconds(1));
        messages += totalMessages(cluster) - messagesBefore;
        bytes += totalBytes(cluster) - bytesBefore;

        final long memberStart = System.nanoTime();
        writer.put(member, value);
        final var writerId = memberId(i % CLUSTER_SIZE);
        awaitCondition(
            () ->
                cluster.running().stream()
                    .allMatch(g -> g.get(member, writerId).filter(value::equals).isPresent()),
            Duration.ofSeconds(30));
        memberLatencies.add(Duration.ofNanos(System.nanoTime() - memberStart).toMillis());
      }

      final boolean countsMessages = transport == Transport.ANTI_ENTROPY;
      report.append(
          String.format(
              Locale.ROOT,
              "| %s | %s | %s | %s | %s |%n",
              transport,
              percentiles(globalLatencies),
              percentiles(memberLatencies),
              countsMessages
                  ? String.valueOf(messages / SAMPLES)
                  : "n/a (UDP gossip, not observable)",
              countsMessages
                  ? String.valueOf(bytes / SAMPLES)
                  : "n/a (UDP gossip, not observable)"));
    }
  }

  /**
   * Background TCP traffic of the whole cluster while nothing changes, with one GLOBAL value of the
   * given size gossiped. SWIM traffic is probes and syncs (topics {@code atomix-membership-*}); it
   * runs for both transports, but only the SWIM gossiper puts the value into it.
   */
  private void measureBackgroundTraffic(final Transport transport, final int size) {
    final GlobalKey<String> key =
        GossipKey.global("compare.background", STRING, MergeStrategy.lastWriterWins(), "");
    try (final var cluster = new TestCluster(transport, Timing.defaults(), CLUSTER_SIZE).start()) {
      awaitMembership(cluster);
      if (size > 0) {
        final var value = "x".repeat(size);
        cluster.gossiper(0).put(key, value);
        awaitCondition(
            () -> cluster.running().stream().allMatch(g -> value.equals(g.get(key))),
            Duration.ofSeconds(60));
        // let pending gossip settle, so only steady-state traffic is measured
        sleepQuietly(Duration.ofSeconds(2));
      }
      final long swimBefore = cluster.tcpRequestBytes("atomix-membership");
      final long gossipBefore = cluster.tcpRequestBytes("cluster-gossip");
      final var window = Duration.ofSeconds(20);
      sleepQuietly(window);
      final long swimPerSecond =
          (cluster.tcpRequestBytes("atomix-membership") - swimBefore) / window.toSeconds();
      final long gossipPerSecond =
          (cluster.tcpRequestBytes("cluster-gossip") - gossipBefore) / window.toSeconds();
      report.append(
          String.format(
              Locale.ROOT,
              "| %s | %d | %d | %d | %d |%n",
              transport,
              size,
              swimPerSecond,
              gossipPerSecond,
              swimPerSecond + gossipPerSecond));
    }
  }

  private String measureValueSize(final Transport transport, final int size) {
    final GlobalKey<String> key =
        GossipKey.global("compare.size", STRING, MergeStrategy.lastWriterWins(), "");
    final var value = "x".repeat(size);
    try (final var cluster = new TestCluster(transport, Timing.fast(), 3).start()) {
      awaitMembership(cluster);
      final long start = System.nanoTime();
      cluster.gossiper(0).put(key, value);
      final boolean converged =
          awaitCondition(
              () -> cluster.running().stream().allMatch(g -> value.equals(g.get(key))),
              Duration.ofSeconds(15));
      final long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
      return converged ? "ok (" + millis + " ms)" : "**did not converge**";
    }
  }

  private void measureLeave(final Transport transport, final int stateSize) {
    final MemberKey<String> key = GossipKey.member("compare.leave", STRING);
    final GlobalKey<String> state =
        GossipKey.global("compare.leave-state", STRING, MergeStrategy.lastWriterWins(), "");
    try (final var cluster = new TestCluster(transport, Timing.defaults(), 3).start()) {
      awaitMembership(cluster);
      if (stateSize > 0) {
        final var value = "x".repeat(stateSize);
        cluster.gossiper(2).put(state, value);
        awaitCondition(
            () -> cluster.running().stream().allMatch(g -> value.equals(g.get(state))),
            Duration.ofSeconds(60));
      }
      cluster.gossiper(0).put(key, "v");
      cluster.gossiper(1).put(key, "v");
      awaitCondition(() -> cluster.gossiper(2).getAll(key).size() == 2, Duration.ofSeconds(30));

      long start = System.nanoTime();
      cluster.stop(0);
      final boolean droppedAfterStop =
          awaitCondition(
              () -> cluster.gossiper(2).get(key, memberId(0)).isEmpty(), Duration.ofSeconds(60));
      final long stopMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

      start = System.nanoTime();
      cluster.restart(1);
      final boolean droppedAfterRestart =
          awaitCondition(
              () -> cluster.gossiper(2).get(key, memberId(1)).isEmpty(), Duration.ofSeconds(60));
      final long restartMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

      report.append(
          String.format(
              Locale.ROOT,
              "| %s | %d | %s | %s |%n",
              transport,
              stateSize,
              droppedAfterStop ? stopMillis : "not within 60 s",
              droppedAfterRestart ? restartMillis : "not within 60 s"));
    }
  }

  private static void awaitMembership(@SuppressWarnings("unused") final TestCluster cluster) {
    // every member must know all others before measuring, or early pushes go nowhere
    sleepQuietly(Duration.ofSeconds(3));
  }

  private static long totalMessages(final TestCluster cluster) {
    return cluster.running().stream().mapToLong(g -> g.stats().messagesSent()).sum();
  }

  private static long totalBytes(final TestCluster cluster) {
    return cluster.running().stream().mapToLong(g -> g.stats().bytesSent()).sum();
  }

  private static String percentiles(final List<Long> samples) {
    final var sorted = new ArrayList<>(samples);
    Collections.sort(sorted);
    return sorted.get(sorted.size() / 2) + " / " + sorted.getLast();
  }

  private static boolean awaitCondition(final BooleanSupplier condition, final Duration timeout) {
    final long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return true;
      }
      // poll every millisecond without burning a core that the cluster under test needs
      LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
    }
    return condition.getAsBoolean();
  }

  private static void sleepQuietly(final Duration duration) {
    final long deadline = System.nanoTime() + duration.toNanos();
    while (System.nanoTime() < deadline) {
      LockSupport.parkNanos(deadline - System.nanoTime());
    }
  }
}
