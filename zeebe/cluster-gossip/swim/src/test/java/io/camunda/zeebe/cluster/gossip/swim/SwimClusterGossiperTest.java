/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.swim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.protocol.SwimMembershipProtocol;
import io.camunda.zeebe.cluster.gossip.GossipCodec;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.MemberKey;
import io.camunda.zeebe.cluster.gossip.MergeStrategy;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the gossiper on real three-member clusters with SWIM on localhost. */
final class SwimClusterGossiperTest {

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

  private static final MemberKey<String> ROLE = GossipKey.member("test.role", STRING);

  private final List<Node> nodes = new ArrayList<>();
  private final List<AtomixCluster> atomixMembers = new ArrayList<>();
  private final List<SwimClusterGossiper> gossipers = new ArrayList<>();

  @BeforeEach
  void setUp() {
    for (int i = 0; i < 3; i++) {
      nodes.add(
          Node.builder()
              .withId(String.valueOf(i))
              .withPort(SocketUtil.getNextAddress().getPort())
              .build());
      atomixMembers.add(null);
      gossipers.add(null);
    }
    for (int i = 0; i < nodes.size(); i++) {
      start(i);
    }
  }

  @AfterEach
  void tearDown() {
    for (int i = 0; i < nodes.size(); i++) {
      stop(i);
    }
  }

  @Test
  void shouldShareMemberValues() {
    // when
    gossiper(0).put(ROLE, "leader");
    gossiper(1).put(ROLE, "follower");

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(gossiper(2).getAll(ROLE))
                    .isEqualTo(Map.of(memberId(0), "leader", memberId(1), "follower")));
  }

  @Test
  void shouldSeeOwnValueImmediately() {
    // when
    gossiper(0).put(ROLE, "leader");

    // then
    assertThat(gossiper(0).get(ROLE, memberId(0))).contains("leader");
  }

  @Test
  void shouldPropagateLatestValue() {
    // given
    gossiper(0).put(ROLE, "follower");
    await().untilAsserted(() -> assertThat(gossiper(2).get(ROLE, memberId(0))).isPresent());

    // when
    gossiper(0).put(ROLE, "leader");

    // then
    await().untilAsserted(() -> assertThat(gossiper(2).get(ROLE, memberId(0))).contains("leader"));
  }

  @Test
  void shouldReplayCurrentValuesAndNotifyChanges() {
    // given
    gossiper(0).put(ROLE, "leader");
    await().untilAsserted(() -> assertThat(gossiper(2).get(ROLE, memberId(0))).isPresent());
    final var received = new CopyOnWriteArrayList<String>();

    // when
    gossiper(2)
        .subscribe(ROLE, Runnable::run, (member, value) -> received.add(member + "=" + value));
    gossiper(1).put(ROLE, "follower");

    // then
    await()
        .untilAsserted(
            () ->
                assertThat(received)
                    .containsExactly(
                        memberId(0) + "=" + Optional.of("leader"),
                        memberId(1) + "=" + Optional.of("follower")));
  }

  @Test
  void shouldNotifyLocalWrites() {
    // given
    final var received = new CopyOnWriteArrayList<Optional<String>>();
    gossiper(0).subscribe(ROLE, Runnable::run, (member, value) -> received.add(value));

    // when
    gossiper(0).put(ROLE, "leader");
    gossiper(0).put(ROLE, "leader");

    // then
    assertThat(received).containsExactly(Optional.of("leader"));
  }

  @Test
  void shouldDropValuesWhenMemberLeaves() {
    // given
    gossiper(0).put(ROLE, "leader");
    await().untilAsserted(() -> assertThat(gossiper(2).get(ROLE, memberId(0))).isPresent());
    final var received = new CopyOnWriteArrayList<Optional<String>>();
    gossiper(2).subscribe(ROLE, Runnable::run, (member, value) -> received.add(value));

    // when
    stop(0);

    // then
    await().untilAsserted(() -> assertThat(gossiper(2).get(ROLE, memberId(0))).isEmpty());
    assertThat(received).containsExactly(Optional.of("leader"), Optional.empty());
  }

  @Test
  void shouldDropValuesOfPreviousRunOnRestart() {
    // given
    gossiper(0).put(ROLE, "leader");
    await().untilAsserted(() -> assertThat(gossiper(2).get(ROLE, memberId(0))).isPresent());

    // when
    stop(0);
    start(0);

    // then
    await().untilAsserted(() -> assertThat(gossiper(2).get(ROLE, memberId(0))).isEmpty());
    gossiper(0).put(ROLE, "follower");
    await()
        .untilAsserted(() -> assertThat(gossiper(2).get(ROLE, memberId(0))).contains("follower"));
  }

  @Test
  void shouldWriteValueAsBase64PropertyNamedLikeKey() {
    // when
    gossiper(0).put(ROLE, "leader");

    // then - the format components already use when writing member properties directly
    assertThat(localProperties(0).getProperty(ROLE.name()))
        .isEqualTo(Base64.getEncoder().encodeToString(STRING.encode("leader")));
  }

  @Test
  void shouldReadValueWrittenDirectlyAsMemberProperty() {
    // given - a component writing its member property directly, as before the gossiper existed
    final var legacyKey = GossipKey.member("test.legacy", STRING);
    final var encoded = Base64.getEncoder().encodeToString(STRING.encode("written directly"));

    // when
    localProperties(0).setProperty(legacyKey.name(), encoded);

    // then
    await()
        .untilAsserted(
            () -> assertThat(gossiper(2).get(legacyKey, memberId(0))).contains("written directly"));
  }

  @Test
  void shouldIgnorePropertiesNoKeyRefersTo() {
    // given - another component's property, which is not even Base64
    final var received = new CopyOnWriteArrayList<String>();
    gossiper(2)
        .subscribe(ROLE, Runnable::run, (member, value) -> received.add(member + "=" + value));
    localProperties(0).setProperty("test.unrelated", "not Base64!");

    // when
    gossiper(0).put(ROLE, "leader");

    // then
    await().untilAsserted(() -> assertThat(gossiper(2).get(ROLE, memberId(0))).contains("leader"));
    assertThat(received).containsExactly(memberId(0) + "=" + Optional.of("leader"));
  }

  @Test
  void shouldTreatUndecodableValueAsAbsent() {
    // given
    final var failingDecoder =
        GossipKey.member(
            ROLE.name(),
            new GossipCodec<String>() {
              @Override
              public byte[] encode(final String value) {
                return STRING.encode(value);
              }

              @Override
              public String decode(final byte[] bytes) {
                throw new IllegalArgumentException("unknown format");
              }
            });
    gossiper(0).put(ROLE, "leader");
    await().untilAsserted(() -> assertThat(gossiper(2).get(ROLE, memberId(0))).isPresent());

    // when
    final var value = gossiper(2).get(failingDecoder, memberId(0));

    // then
    assertThat(value).isEmpty();
    assertThat(gossiper(2).getAll(failingDecoder)).isEmpty();
  }

  @Test
  void shouldRejectGlobalKeys() {
    // given
    final var key = GossipKey.global("test.flag", STRING, MergeStrategy.lastWriterWins(), "off");

    // when - then
    assertThatThrownBy(() -> gossiper(0).get(key))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> gossiper(0).put(key, "on"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> gossiper(0).update(key, value -> value))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> gossiper(0).subscribe(key, Runnable::run, value -> {}))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private Properties localProperties(final int index) {
    return atomixMembers.get(index).getMembershipService().getLocalMember().properties();
  }

  private SwimClusterGossiper gossiper(final int index) {
    return gossipers.get(index);
  }

  private void start(final int index) {
    final var meterRegistry = new SimpleMeterRegistry();
    final var atomix =
        AtomixCluster.builder(meterRegistry)
            .withAddress(nodes.get(index).address())
            .withMemberId(nodes.get(index).id().id())
            .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
            .withMembershipProtocol(
                SwimMembershipProtocol.builder(meterRegistry)
                    .withGossipInterval(Duration.ofMillis(50))
                    .withProbeInterval(Duration.ofMillis(100))
                    .withSyncInterval(Duration.ofSeconds(1))
                    .withFailureTimeout(Duration.ofSeconds(2))
                    .build())
            .build();
    atomix.start().join();
    final var gossiper = new SwimClusterGossiper(atomix.getMembershipService());
    gossiper.start();
    atomixMembers.set(index, atomix);
    gossipers.set(index, gossiper);
  }

  private void stop(final int index) {
    final var gossiper = gossipers.set(index, null);
    final var atomix = atomixMembers.set(index, null);
    if (gossiper != null) {
      gossiper.close();
    }
    if (atomix != null) {
      atomix.stop().join();
    }
  }

  private static MemberId memberId(final int index) {
    return MemberId.from(String.valueOf(index));
  }

  private static ConditionFactory await() {
    return Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(20));
  }
}
