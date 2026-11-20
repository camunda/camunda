/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.poc;

import static io.camunda.zeebe.cluster.gossip.poc.TestCluster.memberId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.cluster.gossip.GlobalKey;
import io.camunda.zeebe.cluster.gossip.GossipCodec;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.MemberKey;
import io.camunda.zeebe.cluster.gossip.MergeStrategy;
import io.camunda.zeebe.cluster.gossip.poc.TestCluster.Timing;
import io.camunda.zeebe.cluster.gossip.poc.TestCluster.Transport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The ClusterGossiper contract (ADR 0001), checked against every proof of concept on real
 * three-member clusters with SWIM.
 */
final class ClusterGossiperPocTest {

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

  private static final GossipCodec<Set<String>> STRING_SET =
      new GossipCodec<>() {
        @Override
        public byte[] encode(final Set<String> value) {
          return String.join(",", new TreeSet<>(value)).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Set<String> decode(final byte[] bytes) {
          final var text = new String(bytes, StandardCharsets.UTF_8);
          return text.isEmpty()
              ? Set.of()
              : Arrays.stream(text.split(",")).collect(Collectors.toUnmodifiableSet());
        }
      };

  private static final GlobalKey<String> FLAG =
      GossipKey.global("poc.flag", STRING, MergeStrategy.lastWriterWins(), "off");
  private static final GlobalKey<Set<String>> UNION =
      GossipKey.global(
          "poc.union",
          STRING_SET,
          MergeStrategy.merging(
              (left, right) -> {
                final var union = new HashSet<>(left);
                union.addAll(right);
                return Set.copyOf(union);
              }),
          Set.of());
  private static final MemberKey<String> ROLE = GossipKey.member("poc.role", STRING);

  private TestCluster cluster;

  @AfterEach
  void tearDown() {
    if (cluster != null) {
      cluster.close();
    }
  }

  @ParameterizedTest
  @EnumSource(Transport.class)
  void shouldConvergeLastWriterWinsValue(final Transport transport) {
    // given
    cluster = new TestCluster(transport, Timing.fast(), 3).start();

    // when
    cluster.gossiper(0).put(FLAG, "on");

    // then
    await()
        .untilAsserted(
            () ->
                cluster
                    .running()
                    .forEach(gossiper -> assertThat(gossiper.get(FLAG)).isEqualTo("on")));
  }

  @ParameterizedTest
  @EnumSource(Transport.class)
  void shouldMergeConcurrentWrites(final Transport transport) {
    // given
    cluster = new TestCluster(transport, Timing.fast(), 3).start();
    cluster.running().forEach(gossiper -> gossiper.get(UNION));

    // when
    for (int i = 0; i < 3; i++) {
      cluster.gossiper(i).put(UNION, Set.of("from-" + i));
    }

    // then
    await()
        .untilAsserted(
            () ->
                cluster
                    .running()
                    .forEach(
                        gossiper ->
                            assertThat(gossiper.get(UNION))
                                .containsExactlyInAnyOrder("from-0", "from-1", "from-2")));
  }

  @ParameterizedTest
  @EnumSource(Transport.class)
  void shouldDeliverCustomMergeValueToMemberRegisteringLate(final Transport transport) {
    // given - member 2 has not used the key, so it drops the value at first
    cluster = new TestCluster(transport, Timing.fast(), 3).start();
    cluster.gossiper(0).put(UNION, Set.of("x"));
    await().untilAsserted(() -> assertThat(cluster.gossiper(1).get(UNION)).containsExactly("x"));

    // when
    final var lateValue = cluster.gossiper(2).get(UNION);

    // then
    assertThat(lateValue).isNotNull();
    await().untilAsserted(() -> assertThat(cluster.gossiper(2).get(UNION)).containsExactly("x"));
  }

  @ParameterizedTest
  @EnumSource(Transport.class)
  void shouldShareMemberValues(final Transport transport) {
    // given
    cluster = new TestCluster(transport, Timing.fast(), 3).start();

    // when
    cluster.gossiper(0).put(ROLE, "leader");
    cluster.gossiper(1).put(ROLE, "follower");

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(cluster.gossiper(2).getAll(ROLE))
                    .containsEntry(memberId(0), "leader")
                    .containsEntry(memberId(1), "follower"));
  }

  @ParameterizedTest
  @EnumSource(Transport.class)
  void shouldDropMemberValuesWhenMemberLeaves(final Transport transport) {
    // given
    cluster = new TestCluster(transport, Timing.fast(), 3).start();
    cluster.gossiper(0).put(ROLE, "leader");
    await().untilAsserted(() -> assertThat(cluster.gossiper(2).get(ROLE, memberId(0))).isPresent());

    // when
    cluster.stop(0);

    // then
    await().untilAsserted(() -> assertThat(cluster.gossiper(2).get(ROLE, memberId(0))).isEmpty());
  }

  @ParameterizedTest
  @EnumSource(Transport.class)
  void shouldDropMemberValuesOfPreviousRunOnRestart(final Transport transport) {
    // given
    cluster = new TestCluster(transport, Timing.fast(), 3).start();
    cluster.gossiper(0).put(ROLE, "leader");
    await().untilAsserted(() -> assertThat(cluster.gossiper(2).get(ROLE, memberId(0))).isPresent());

    // when
    final var restarted = cluster.restart(0);

    // then
    await().untilAsserted(() -> assertThat(cluster.gossiper(2).get(ROLE, memberId(0))).isEmpty());
    restarted.put(ROLE, "follower");
    await()
        .untilAsserted(
            () -> assertThat(cluster.gossiper(2).get(ROLE, memberId(0))).contains("follower"));
  }

  @ParameterizedTest
  @EnumSource(Transport.class)
  void shouldKeepGlobalValueAfterWriterLeaves(final Transport transport) {
    // given
    cluster = new TestCluster(transport, Timing.fast(), 3).start();
    cluster.gossiper(0).put(FLAG, "on");
    await()
        .untilAsserted(
            () ->
                cluster
                    .running()
                    .forEach(gossiper -> assertThat(gossiper.get(FLAG)).isEqualTo("on")));

    // when - the writer leaves, and a fresh run of another member joins
    cluster.stop(0);
    final var rejoined = cluster.restart(1);

    // then
    await().untilAsserted(() -> assertThat(rejoined.get(FLAG)).isEqualTo("on"));
  }

  private static org.awaitility.core.ConditionFactory await() {
    return Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(20));
  }
}
