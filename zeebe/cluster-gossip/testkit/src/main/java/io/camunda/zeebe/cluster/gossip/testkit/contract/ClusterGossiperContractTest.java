/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.testkit.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.cluster.gossip.ClusterGossiper;
import io.camunda.zeebe.cluster.gossip.GlobalKey;
import io.camunda.zeebe.cluster.gossip.GossipKey;
import io.camunda.zeebe.cluster.gossip.MemberKey;
import io.camunda.zeebe.cluster.gossip.MemberListener;
import io.camunda.zeebe.cluster.gossip.MergeStrategy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The behaviour every {@link ClusterGossiper} implementation must show, as JUnit tests. An
 * implementation runs them by extending this class and starting its cluster:
 *
 * <pre>{@code
 * final class MyGossiperContractTest extends ClusterGossiperContractTest {
 *   @Override
 *   protected GossipTestCluster startCluster(final int size) {
 *     return new MyTestCluster(size);
 *   }
 * }
 * }</pre>
 *
 * <p>Every test gets a fresh cluster of three members, with indexes 0, 1 and 2, and closes it
 * afterwards. Tests only assume what the contract promises: writes are visible locally right away
 * and reach other members eventually, so every assertion about another member's view goes through
 * {@link GossipTestCluster#awaitAsserted(Runnable)}.
 */
public abstract class ClusterGossiperContractTest {

  private static final MemberKey<String> MEMBER_ROLE =
      GossipKey.member("contract.role", ContractCodecs.STRING);
  private static final MemberKey<String> SUBSCRIBED_ROLE =
      GossipKey.member("contract.subscribed-role", ContractCodecs.STRING);
  private static final GlobalKey<String> GLOBAL_FLAG =
      GossipKey.global(
          "contract.flag", ContractCodecs.STRING, MergeStrategy.lastWriterWins(), "off");
  private static final GlobalKey<Set<String>> GLOBAL_UNION =
      GossipKey.global(
          "contract.union",
          ContractCodecs.STRING_SET,
          MergeStrategy.merging(
              (left, right) -> {
                final var union = new HashSet<>(left);
                union.addAll(right);
                return Set.copyOf(union);
              }),
          Set.of());

  private @Nullable GossipTestCluster cluster;

  /** Starts a cluster of the given number of members, ready to use. */
  protected abstract GossipTestCluster startCluster(int size);

  @BeforeEach
  final void startContractCluster() {
    cluster = startCluster(3);
  }

  @AfterEach
  final void closeContractCluster() {
    if (cluster != null) {
      cluster.close();
    }
  }

  private GossipTestCluster cluster() {
    if (cluster == null) {
      throw new IllegalStateException("Expected the cluster to be started before the test");
    }
    return cluster;
  }

  private ClusterGossiper gossiper(final int index) {
    return cluster().gossiper(index);
  }

  private MemberId memberId(final int index) {
    return cluster().memberId(index);
  }

  private void stopMember(final int index) {
    cluster().stop(index);
  }

  private ClusterGossiper restartMember(final int index) {
    return cluster().restart(index);
  }

  private void awaitAsserted(final Runnable assertion) {
    cluster().awaitAsserted(assertion);
  }

  /** Reading and writing MEMBER values (ADR 0001, D9 and D10). */
  @Nested
  final class MemberValues {

    @Test
    void shouldSeeOwnMemberValueImmediately() {
      // when
      gossiper(0).put(MEMBER_ROLE, "leader");

      // then
      assertThat(gossiper(0).get(MEMBER_ROLE, memberId(0))).contains("leader");
      assertThat(gossiper(0).getAll(MEMBER_ROLE)).containsEntry(memberId(0), "leader");
    }

    @Test
    void shouldShareMemberValues() {
      // when
      gossiper(0).put(MEMBER_ROLE, "leader");
      gossiper(1).put(MEMBER_ROLE, "follower");

      // then
      awaitAsserted(
          () ->
              assertThat(gossiper(2).getAll(MEMBER_ROLE))
                  .isEqualTo(Map.of(memberId(0), "leader", memberId(1), "follower")));
    }

    @Test
    void shouldPropagateLatestMemberValue() {
      // given
      gossiper(0).put(MEMBER_ROLE, "follower");
      awaitAsserted(
          () -> assertThat(gossiper(2).get(MEMBER_ROLE, memberId(0))).contains("follower"));

      // when
      gossiper(0).put(MEMBER_ROLE, "leader");

      // then
      awaitAsserted(() -> assertThat(gossiper(2).get(MEMBER_ROLE, memberId(0))).contains("leader"));
    }

    @Test
    void shouldDropMemberValuesWhenMemberLeaves() {
      // given
      gossiper(0).put(MEMBER_ROLE, "leader");
      awaitAsserted(() -> assertThat(gossiper(2).get(MEMBER_ROLE, memberId(0))).isPresent());

      // when
      stopMember(0);

      // then
      awaitAsserted(() -> assertThat(gossiper(2).get(MEMBER_ROLE, memberId(0))).isEmpty());
    }

    @Test
    void shouldDropMemberValuesOfPreviousRunOnRestart() {
      // given
      gossiper(0).put(MEMBER_ROLE, "leader");
      awaitAsserted(() -> assertThat(gossiper(2).get(MEMBER_ROLE, memberId(0))).isPresent());

      // when
      final var restarted = restartMember(0);

      // then
      assertThat(restarted.get(MEMBER_ROLE, memberId(0))).isEmpty();
      awaitAsserted(() -> assertThat(gossiper(2).get(MEMBER_ROLE, memberId(0))).isEmpty());
      restarted.put(MEMBER_ROLE, "follower");
      awaitAsserted(
          () -> assertThat(gossiper(2).get(MEMBER_ROLE, memberId(0))).contains("follower"));
    }

    @Test
    void shouldTreatUndecodableMemberValueAsAbsent() {
      // given - a reader whose codec cannot decode what the writer wrote
      final var unreadable = GossipKey.member(MEMBER_ROLE.name(), ContractCodecs.FAILING_DECODER);
      gossiper(0).put(MEMBER_ROLE, "leader");
      awaitAsserted(() -> assertThat(gossiper(2).get(MEMBER_ROLE, memberId(0))).isPresent());

      // when
      final var value = gossiper(2).get(unreadable, memberId(0));

      // then
      assertThat(value).isEmpty();
      assertThat(gossiper(2).getAll(unreadable)).isEmpty();
    }
  }

  /** Subscribing to MEMBER values (ADR 0001, D13). */
  @Nested
  final class MemberSubscriptions {

    @Test
    void shouldReplayCurrentMemberValuesOnSubscribe() {
      // given
      gossiper(0).put(SUBSCRIBED_ROLE, "leader");
      awaitAsserted(() -> assertThat(gossiper(2).get(SUBSCRIBED_ROLE, memberId(0))).isPresent());
      final var received = new CopyOnWriteArrayList<String>();

      // when
      gossiper(2)
          .subscribe(
              SUBSCRIBED_ROLE,
              Runnable::run,
              (member, value) -> received.add(member + "=" + value));

      // then
      assertThat(received).containsExactly(memberId(0) + "=" + Optional.of("leader"));
    }

    @Test
    void shouldNotifyLocalMemberWritesOncePerChange() {
      // given
      final var received = new CopyOnWriteArrayList<Optional<String>>();
      gossiper(0).subscribe(SUBSCRIBED_ROLE, Runnable::run, (member, value) -> received.add(value));

      // when
      gossiper(0).put(SUBSCRIBED_ROLE, "leader");
      gossiper(0).put(SUBSCRIBED_ROLE, "leader");

      // then
      assertThat(received).containsExactly(Optional.of("leader"));
    }

    @Test
    void shouldNotifyRemoteMemberWrites() {
      // given
      final var received = new CopyOnWriteArrayList<String>();
      gossiper(2)
          .subscribe(
              SUBSCRIBED_ROLE,
              Runnable::run,
              (member, value) -> received.add(member + "=" + value));

      // when
      gossiper(1).put(SUBSCRIBED_ROLE, "follower");

      // then
      awaitAsserted(
          () -> assertThat(received).containsExactly(memberId(1) + "=" + Optional.of("follower")));
    }

    @Test
    void shouldNotifyEmptyValueWhenMemberLeaves() {
      // given
      gossiper(0).put(SUBSCRIBED_ROLE, "leader");
      awaitAsserted(() -> assertThat(gossiper(2).get(SUBSCRIBED_ROLE, memberId(0))).isPresent());
      final var received = new CopyOnWriteArrayList<Optional<String>>();
      gossiper(2).subscribe(SUBSCRIBED_ROLE, Runnable::run, (member, value) -> received.add(value));

      // when
      stopMember(0);

      // then
      awaitAsserted(
          () -> assertThat(received).containsExactly(Optional.of("leader"), Optional.empty()));
    }

    @Test
    void shouldRunCallbacksOnGivenExecutor() {
      // given
      final Queue<Runnable> queued = new ConcurrentLinkedQueue<>();
      final var received = new CopyOnWriteArrayList<Optional<String>>();
      gossiper(0).subscribe(SUBSCRIBED_ROLE, queued::add, (member, value) -> received.add(value));

      // when
      gossiper(0).put(SUBSCRIBED_ROLE, "leader");

      // then
      assertThat(received).isEmpty();
      queued.forEach(Runnable::run);
      assertThat(received).containsExactly(Optional.of("leader"));
    }

    @Test
    void shouldStopNotifyingAfterClose() {
      // given
      final var received = new CopyOnWriteArrayList<Optional<String>>();
      final var subscription =
          gossiper(0)
              .subscribe(SUBSCRIBED_ROLE, Runnable::run, (member, value) -> received.add(value));

      // when
      subscription.close();
      gossiper(0).put(SUBSCRIBED_ROLE, "leader");

      // then
      assertThat(received).isEmpty();
    }

    @Test
    void shouldStopOnlyClosedSubscriptionOfSameListener() {
      // given - the same listener subscribed twice with the same executor
      final var received = new CopyOnWriteArrayList<Optional<String>>();
      final MemberListener<String> listener = (member, value) -> received.add(value);
      final Executor executor = Runnable::run;
      final var first = gossiper(0).subscribe(SUBSCRIBED_ROLE, executor, listener);
      gossiper(0).subscribe(SUBSCRIBED_ROLE, executor, listener);

      // when
      first.close();
      gossiper(0).put(SUBSCRIBED_ROLE, "leader");

      // then
      assertThat(received).containsExactly(Optional.of("leader"));
    }

    @Test
    void shouldLetListenerWriteWhileValuesAreReplayed() {
      // given - member 1 holds member 0's value but none of its own yet
      gossiper(0).put(SUBSCRIBED_ROLE, "leader");
      awaitAsserted(() -> assertThat(gossiper(1).get(SUBSCRIBED_ROLE, memberId(0))).isPresent());

      // when - member 1 reacts to the replayed value by writing its own
      gossiper(1)
          .subscribe(
              SUBSCRIBED_ROLE,
              Runnable::run,
              (member, value) -> {
                if (member.equals(memberId(0))) {
                  gossiper(1).put(SUBSCRIBED_ROLE, "follower");
                }
              });

      // then
      assertThat(gossiper(1).get(SUBSCRIBED_ROLE, memberId(1))).contains("follower");
    }

    @Test
    void shouldNotifyEveryListenerInOrderWhenListenerWrites() {
      // given - the first listener answers "leader" by writing "follower"
      gossiper(0)
          .subscribe(
              SUBSCRIBED_ROLE,
              Runnable::run,
              (member, value) -> {
                if (value.equals(Optional.of("leader"))) {
                  gossiper(0).put(SUBSCRIBED_ROLE, "follower");
                }
              });
      final var received = new CopyOnWriteArrayList<Optional<String>>();
      gossiper(0).subscribe(SUBSCRIBED_ROLE, Runnable::run, (member, value) -> received.add(value));

      // when
      gossiper(0).put(SUBSCRIBED_ROLE, "leader");

      // then
      assertThat(received).containsExactly(Optional.of("leader"), Optional.of("follower"));
    }

    @Test
    void shouldKeepNotifyingWhenListenerThrows() {
      // given
      final var failures = new CopyOnWriteArrayList<Optional<String>>();
      gossiper(0)
          .subscribe(
              SUBSCRIBED_ROLE,
              Runnable::run,
              (member, value) -> {
                failures.add(value);
                throw new IllegalStateException("listener failure");
              });
      final var received = new CopyOnWriteArrayList<Optional<String>>();
      gossiper(0).subscribe(SUBSCRIBED_ROLE, Runnable::run, (member, value) -> received.add(value));

      // when
      gossiper(0).put(SUBSCRIBED_ROLE, "leader");
      gossiper(0).put(SUBSCRIBED_ROLE, "follower");

      // then
      assertThat(received).containsExactly(Optional.of("leader"), Optional.of("follower"));
      assertThat(failures).containsExactly(Optional.of("leader"), Optional.of("follower"));
    }

    @Test
    void shouldDeliverAgainAfterExecutorRejectedCallback() {
      // given - an executor that rejects its first task
      final var rejectNext = new AtomicBoolean(true);
      final Executor executor =
          task -> {
            if (rejectNext.getAndSet(false)) {
              throw new RejectedExecutionException("busy");
            }
            task.run();
          };
      final var received = new CopyOnWriteArrayList<Optional<String>>();
      gossiper(0).subscribe(SUBSCRIBED_ROLE, executor, (member, value) -> received.add(value));
      gossiper(0).put(SUBSCRIBED_ROLE, "leader");

      // when
      gossiper(0).put(SUBSCRIBED_ROLE, "follower");

      // then
      assertThat(received).containsExactly(Optional.of("leader"), Optional.of("follower"));
    }
  }

  /**
   * Reading, writing and subscribing to GLOBAL values (ADR 0001, D5, D7, D8, D11 and D13). Skipped
   * for implementations that do not support GLOBAL keys.
   */
  @Nested
  final class GlobalValues {

    @BeforeEach
    void requireGlobalKeys() {
      assumeTrue(cluster().supportsGlobalKeys(), "the implementation has no GLOBAL keys");
    }

    @Test
    void shouldReturnDefaultUntilGlobalValueIsWritten() {
      // when
      final var value = gossiper(0).get(GLOBAL_FLAG);

      // then
      assertThat(value).isEqualTo("off");
    }

    @Test
    void shouldPropagateGlobalWrite() {
      // when
      gossiper(0).put(GLOBAL_FLAG, "on");

      // then
      assertThat(gossiper(0).get(GLOBAL_FLAG)).isEqualTo("on");
      awaitAsserted(
          () ->
              assertThat(List.of(gossiper(1).get(GLOBAL_FLAG), gossiper(2).get(GLOBAL_FLAG)))
                  .containsOnly("on"));
    }

    @Test
    void shouldUpdateGlobalValueFromCurrentValue() {
      // given
      gossiper(0).put(GLOBAL_FLAG, "on");

      // when
      gossiper(0).update(GLOBAL_FLAG, value -> value + "!");

      // then
      assertThat(gossiper(0).get(GLOBAL_FLAG)).isEqualTo("on!");
    }

    @Test
    void shouldMergeConcurrentGlobalWrites() {
      // when
      gossiper(0).put(GLOBAL_UNION, Set.of("a"));
      gossiper(1).put(GLOBAL_UNION, Set.of("b"));

      // then
      awaitAsserted(
          () ->
              assertThat(
                      List.of(
                          gossiper(0).get(GLOBAL_UNION),
                          gossiper(1).get(GLOBAL_UNION),
                          gossiper(2).get(GLOBAL_UNION)))
                  .containsOnly(Set.of("a", "b")));
    }

    @Test
    void shouldKeepGlobalValueAfterWriterRestarts() {
      // given
      gossiper(0).put(GLOBAL_FLAG, "on");
      awaitAsserted(() -> assertThat(gossiper(2).get(GLOBAL_FLAG)).isEqualTo("on"));

      // when
      final var restarted = restartMember(0);

      // then
      awaitAsserted(() -> assertThat(restarted.get(GLOBAL_FLAG)).isEqualTo("on"));
    }

    @Test
    void shouldReplayAndNotifyGlobalValue() {
      // given
      final var received = new CopyOnWriteArrayList<String>();
      gossiper(0).subscribe(GLOBAL_FLAG, Runnable::run, received::add);

      // when
      gossiper(1).put(GLOBAL_FLAG, "on");

      // then
      awaitAsserted(() -> assertThat(received).containsExactly("off", "on"));
    }

    @Test
    void shouldRejectKeyNameReusedWithDifferentScope() {
      // given
      gossiper(0).get(GLOBAL_FLAG);
      final var member = GossipKey.member(GLOBAL_FLAG.name(), ContractCodecs.STRING);

      // when - then
      assertThatThrownBy(() -> gossiper(0).put(member, "on"))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectKeyNameReusedWithDifferentMergeKind() {
      // given
      gossiper(0).get(GLOBAL_FLAG);
      final var merging =
          GossipKey.global(
              GLOBAL_FLAG.name(),
              ContractCodecs.STRING,
              MergeStrategy.<String>merging((left, right) -> left),
              "off");

      // when - then
      assertThatThrownBy(() -> gossiper(0).get(merging)).isInstanceOf(IllegalStateException.class);
    }
  }
}
