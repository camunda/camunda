/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.cluster.gossip.ClusterGossiper;
import io.camunda.zeebe.cluster.gossip.GlobalKey;
import io.camunda.zeebe.cluster.gossip.GlobalListener;
import io.camunda.zeebe.cluster.gossip.GossipCodec;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.MemberKey;
import io.camunda.zeebe.cluster.gossip.MergeStrategy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class InMemoryGossipClusterTest {

  private static final MemberId A = MemberId.from(0);
  private static final MemberId B = MemberId.from(1);
  private static final MemberId C = MemberId.from(2);

  private static final GlobalKey<String> FLAG =
      GossipKey.global("flag", TestCodecs.STRING, MergeStrategy.lastWriterWins(), "off");
  private static final GlobalKey<Set<String>> UNION =
      GossipKey.global(
          "union",
          TestCodecs.STRING_SET,
          MergeStrategy.merging(
              (left, right) -> {
                final var union = new HashSet<>(left);
                union.addAll(right);
                return Set.copyOf(union);
              }),
          Set.of());
  private static final MemberKey<String> ROLE = GossipKey.member("role", TestCodecs.STRING);

  private final AtomicLong clock = new AtomicLong(1);
  private final InMemoryGossipCluster cluster = new InMemoryGossipCluster(clock::get);
  private ClusterGossiper a;
  private ClusterGossiper b;
  private ClusterGossiper c;

  @BeforeEach
  void setUp() {
    a = cluster.addMember(A);
    b = cluster.addMember(B);
    c = cluster.addMember(C);
  }

  @Nested
  final class GlobalLastWriterWins {

    @Test
    void shouldKeepLaterWriteRegardlessOfArrivalOrder() {
      // given
      b.put(FLAG, "early");
      clock.set(2);
      a.put(FLAG, "late");

      // when
      cluster.exchange(A, C);
      cluster.exchange(B, C);

      // then
      assertThat(c.get(FLAG)).isEqualTo("late");
    }

    @Test
    void shouldBreakTimestampTieByHigherMemberId() {
      // given - both write at the same time
      b.put(FLAG, "from-b");
      a.put(FLAG, "from-a");

      // when
      cluster.sync();

      // then
      assertThat(List.of(a.get(FLAG), b.get(FLAG), c.get(FLAG))).containsOnly("from-b");
    }

    @Test
    void shouldConvergeWhenRestartedWriterReusesTimestamp() {
      // given - the restarted run writes with the same timestamp as the previous run
      a.put(FLAG, "before-restart");
      cluster.sync();
      final var restarted = cluster.restart(A);
      restarted.put(FLAG, "after-restart");

      // when
      cluster.sync();

      // then
      assertThat(List.of(b.get(FLAG), c.get(FLAG))).containsOnly(restarted.get(FLAG));
    }

    @Test
    void shouldSpreadNewerTimestampOfSameValueOnSync() {
      // given - C rewrites the value everyone holds, which only raises its timestamp
      a.put(FLAG, "on");
      cluster.sync();
      c.put(FLAG, "on");
      cluster.disconnect(A, C);
      cluster.sync();

      // when - A writes after the sync, so its write must win
      a.put(FLAG, "off-again");
      cluster.sync();

      // then
      assertThat(List.of(a.get(FLAG), b.get(FLAG), c.get(FLAG))).containsOnly("off-again");
    }

    @Test
    void shouldLetLocalWriteWinWhileClockStandsStill() {
      // given
      a.put(FLAG, "first");

      // when
      a.put(FLAG, "second");

      // then
      assertThat(a.get(FLAG)).isEqualTo("second");
    }

    @Test
    void shouldLoseValueWhenEveryHolderRestarts() {
      // given
      a.put(FLAG, "on");
      cluster.sync();

      // when
      cluster.restart(A);
      cluster.restart(B);
      final var restartedC = cluster.restart(C);
      cluster.sync();

      // then
      assertThat(restartedC.get(FLAG)).isEqualTo("off");
    }
  }

  @Nested
  final class GlobalCustomMerge {

    @Test
    void shouldMergeLocalWriteWithCurrentValue() {
      // given
      a.put(UNION, Set.of("x"));

      // when
      a.put(UNION, Set.of("y"));

      // then
      assertThat(a.get(UNION)).containsExactlyInAnyOrder("x", "y");
    }

    @Test
    void shouldDeliverValueOnceKeyIsRegistered() {
      // given - c has never used the key, so it cannot merge values for it
      a.put(UNION, Set.of("x"));
      cluster.exchange(A, C);

      // when
      final var beforeRegistration = c.get(UNION);
      cluster.exchange(A, C);

      // then
      assertThat(beforeRegistration).isEmpty();
      assertThat(c.get(UNION)).containsExactly("x");
    }

    @Test
    void shouldFailToSyncWhenMergeDoesNotConverge() {
      // given - every merge produces a new value
      final var counter = new AtomicLong();
      final var key =
          GossipKey.global(
              "diverging",
              TestCodecs.LONG,
              MergeStrategy.<Long>merging((left, right) -> counter.incrementAndGet()),
              0L);
      a.put(key, 1L);
      b.put(key, 2L);

      // when - then
      assertThatThrownBy(cluster::sync)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("converge");
    }
  }

  @Nested
  final class Partitions {

    @Test
    void shouldNotDeliverToIsolatedMember() {
      // given
      cluster.isolate(C);
      a.put(FLAG, "on");

      // when
      cluster.sync();

      // then
      assertThat(b.get(FLAG)).isEqualTo("on");
      assertThat(c.get(FLAG)).isEqualTo("off");
    }

    @Test
    void shouldConvergeAfterHeal() {
      // given
      cluster.isolate(C);
      a.put(FLAG, "on");
      c.put(ROLE, "leader");
      cluster.sync();

      // when
      cluster.heal();
      cluster.sync();

      // then
      assertThat(c.get(FLAG)).isEqualTo("on");
      assertThat(a.get(ROLE, C)).contains("leader");
    }

    @Test
    void shouldRejectExchangeOfMemberWithItself() {
      // when - then
      assertThatThrownBy(() -> cluster.exchange(A, A))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("two different members");
    }

    @Test
    void shouldRejectExchangeBetweenDisconnectedMembers() {
      // given
      cluster.disconnect(A, B);

      // when - then
      assertThatThrownBy(() -> cluster.exchange(A, B))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("disconnected");
    }
  }

  @Nested
  final class Subscriptions {

    @Test
    void shouldReplayCurrentGlobalValueOnSubscribe() {
      // given
      final var received = new ArrayList<String>();

      // when
      a.subscribe(FLAG, Runnable::run, received::add);

      // then
      assertThat(received).containsExactly("off");
    }

    @Test
    void shouldNotifyOnLocalAndRemoteGlobalWrites() {
      // given
      final var received = new ArrayList<String>();
      b.subscribe(FLAG, Runnable::run, received::add);

      // when
      b.put(FLAG, "local");
      clock.set(2);
      a.put(FLAG, "remote");
      cluster.sync();

      // then
      assertThat(received).containsExactly("off", "local", "remote");
    }

    @Test
    void shouldRunCallbacksOnGivenExecutor() {
      // given
      final var queued = new ArrayList<Runnable>();
      final var received = new ArrayList<String>();
      a.subscribe(FLAG, queued::add, received::add);

      // when
      a.put(FLAG, "on");

      // then
      assertThat(received).isEmpty();
      queued.forEach(Runnable::run);
      assertThat(received).containsExactly("off", "on");
    }

    @Test
    void shouldNotRunQueuedCallbacksOfPreviousRunAfterRestart() {
      // given
      final var queued = new ArrayList<Runnable>();
      final var received = new ArrayList<Optional<String>>();
      a.subscribe(ROLE, queued::add, (member, value) -> received.add(value));
      a.put(ROLE, "leader");

      // when
      cluster.restart(A);
      queued.forEach(Runnable::run);

      // then
      assertThat(received).isEmpty();
    }

    @Test
    void shouldStopOnlyClosedSubscriptionOfSameListener() {
      // given - the same listener subscribed twice with the same executor
      final var received = new ArrayList<String>();
      final GlobalListener<String> listener = received::add;
      final Executor executor = Runnable::run;
      final var first = a.subscribe(FLAG, executor, listener);
      a.subscribe(FLAG, executor, listener);
      received.clear();

      // when
      first.close();
      a.put(FLAG, "on");

      // then
      assertThat(received).containsExactly("on");
    }

    @Test
    void shouldStopNotifyingAfterClose() {
      // given
      final var received = new ArrayList<String>();
      final var subscription = a.subscribe(FLAG, Runnable::run, received::add);

      // when
      subscription.close();
      a.put(FLAG, "on");

      // then
      assertThat(received).containsExactly("off");
    }
  }

  @Nested
  final class Registration {

    @Test
    void shouldRejectNameReusedWithDifferentScope() {
      // given
      a.put(FLAG, "on");
      final var sameNameAsMemberKey = GossipKey.member(FLAG.name(), TestCodecs.STRING);

      // when - then
      assertThatThrownBy(() -> a.put(sameNameAsMemberKey, "x"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("MEMBER");
    }

    @Test
    void shouldRejectNameReusedWithDifferentMergeKind() {
      // given
      a.get(FLAG);
      final var sameNameCustomMerge =
          GossipKey.global(
              FLAG.name(), TestCodecs.STRING, MergeStrategy.merging((x, y) -> x), "off");

      // when - then
      assertThatThrownBy(() -> a.get(sameNameCustomMerge))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("custom merge");
    }

    @Test
    void shouldNotNotifyUndecodableNewValue() {
      // given
      final var failingDecoder =
          GossipKey.member(
              ROLE.name(),
              new GossipCodec<String>() {
                @Override
                public byte[] encode(final String value) {
                  return TestCodecs.STRING.encode(value);
                }

                @Override
                public String decode(final byte[] bytes) {
                  throw new IllegalArgumentException("unknown format");
                }
              });
      final var received = new ArrayList<Optional<String>>();
      b.subscribe(failingDecoder, Runnable::run, (member, value) -> received.add(value));

      // when
      a.put(ROLE, "leader");
      cluster.sync();

      // then
      assertThat(received).isEmpty();
    }

    @Test
    void shouldShowUndecodableValueAsAbsent() {
      // given
      final GossipCodec<String> failingDecoder =
          new GossipCodec<>() {
            @Override
            public byte[] encode(final String value) {
              return TestCodecs.STRING.encode(value);
            }

            @Override
            public String decode(final byte[] bytes) {
              throw new IllegalArgumentException("unknown format");
            }
          };
      a.put(FLAG, "on");
      a.put(ROLE, "leader");
      cluster.sync();

      // when
      final var global =
          b.get(
              GossipKey.global(
                  FLAG.name(), failingDecoder, MergeStrategy.lastWriterWins(), "fallback"));
      final var member = b.get(GossipKey.member(ROLE.name(), failingDecoder), A);

      // then
      assertThat(global).isEqualTo("fallback");
      assertThat(member).isEmpty();
    }

    @Test
    void shouldRejectUseOfGossiperFromPreviousRun() {
      // given
      cluster.restart(A);

      // when - then
      assertThatThrownBy(() -> a.get(FLAG))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("no longer in the cluster");
    }
  }
}
