/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import io.camunda.zeebe.mailbox.leadership.LeadershipListener.Reason;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Two regions with two nodes each, and four partitions. The tests drive the rounds of each node
 * themselves, on one clock that only they move.
 */
final class LeaderElectorTest {

  private static final LeaseConfig CONFIG =
      new LeaseConfig(Duration.ofSeconds(9), Duration.ofSeconds(3), Duration.ofSeconds(5));
  private static final Set<Integer> PARTITIONS = Set.of(1, 2, 3, 4);

  private final AtomicLong clock = new AtomicLong();
  private final FlakyStore store = new FlakyStore(new InMemoryLeaseStore());
  private final List<String> eastAlive = new CopyOnWriteArrayList<>(List.of("e1", "e2"));
  private final List<String> westAlive = new CopyOnWriteArrayList<>(List.of("w1", "w2"));
  private final Node e1 = new Node("e1", "east", eastAlive);
  private final Node e2 = new Node("e2", "east", eastAlive);
  private final Node w1 = new Node("w1", "west", westAlive);
  private final Node w2 = new Node("w2", "west", westAlive);

  @Test
  void shouldElectOneLeaderForEachPartition() {
    // when
    tick(e1, e2, w1, w2);

    // then - the east region read the rows first, and its nodes split the partitions
    assertThat(e1.elector.leading()).containsOnlyKeys(1, 3);
    assertThat(e2.elector.leading()).containsOnlyKeys(2, 4);
    assertThat(w1.elector.leading()).isEmpty();
    assertThat(w2.elector.leading()).isEmpty();
  }

  @Test
  void shouldKeepTheLeadersWhileTheyRenew() {
    // given
    tick(e1, e2, w1, w2);

    // when - much longer than the lease, with renewals
    for (int round = 0; round < 10; round++) {
      advance(CONFIG.renewInterval());
      tick(e1, e2, w1, w2);
    }

    // then
    assertThat(e1.elector.leading()).containsOnlyKeys(1, 3);
    assertThat(e2.elector.leading()).containsOnlyKeys(2, 4);
    assertThat(w1.events).isEmpty();
    assertThat(w2.events).isEmpty();
  }

  @Test
  void shouldReplaceAFailedLeaderInTheSameRegionWithoutWaitingForTheLease() {
    // given
    tick(e1, e2, w1, w2);

    // when - gossip no longer sees e1
    eastAlive.remove("e1");
    tick(e2, w1, w2);

    // then
    assertThat(e2.elector.leading())
        .containsOnly(Map.entry(1, 2L), Map.entry(2, 1L), Map.entry(3, 2L), Map.entry(4, 1L));
    assertThat(w1.elector.leading()).isEmpty();
  }

  @Test
  void shouldMoveThePartitionsOfAFailedRegionAfterTheLease() {
    // given
    tick(e1, e2, w1, w2);

    // when - the east region fails, and the west region keeps reading the rows
    advance(CONFIG.renewInterval());
    tick(w1, w2);
    advance(CONFIG.renewInterval());
    tick(w1, w2);
    final var beforeTheLeaseRanOut = Map.copyOf(w1.elector.leading());
    advance(CONFIG.leaseDuration());
    tick(w1, w2);

    // then
    assertThat(beforeTheLeaseRanOut).isEmpty();
    assertThat(w1.elector.leading()).containsOnlyKeys(1, 3);
    assertThat(w2.elector.leading()).containsOnlyKeys(2, 4);
    assertThat(store.findLeases()).extracting(PartitionLease::term).containsOnly(2L);
  }

  @Test
  void shouldStopLeadingByItselfBeforeAnotherRegionCanClaim() {
    // given
    tick(e1, e2, w1, w2);
    store.failWritesOf("e1");

    // when - e1 cannot write for longer than fenceAfter, but less than the lease
    advance(CONFIG.renewInterval());
    tick(e1, e2, w1, w2);
    advance(CONFIG.renewInterval());
    tick(e1, e2, w1, w2);

    // then
    assertThat(e1.events).contains("not leading 1 FENCED", "not leading 3 FENCED");
    assertThat(e1.elector.leading()).isEmpty();
    assertThat(w1.elector.leading()).isEmpty();
  }

  @Test
  void shouldShowInTheMetricsWhichPartitionsTheNodeLeads() {
    // given
    tick(e1, e2, w1, w2);
    store.failWritesOf("e1");

    // when - e1 cannot renew for longer than fenceAfter
    advance(CONFIG.renewInterval());
    tick(e1);
    advance(CONFIG.renewInterval());
    tick(e1);

    // then
    assertThat(e1.gauge(LeadershipMetrics.LEADER, 1)).isZero();
    assertThat(e1.gauge(LeadershipMetrics.TERM, 1)).isEqualTo(1);
    assertThat(e1.transitions(1, "claimed")).isEqualTo(1);
    assertThat(e1.transitions(1, "fenced")).isEqualTo(1);
    assertThat(e2.gauge(LeadershipMetrics.LEADER, 2)).isEqualTo(1);
    assertThat(e2.gauge(LeadershipMetrics.LEADER, 1)).isZero();
  }

  @Test
  void shouldMeasureTheTakeoverOfAFailedLeaderInTheSameRegion() {
    // given
    tick(e1, e2, w1, w2);

    // when - gossip no longer sees e1, a lease interval later
    advance(CONFIG.renewInterval());
    eastAlive.remove("e1");
    tick(e2);

    // then - e2 claims at once, so the delay is since its first read of the lease
    final var takeover = e2.takeover(1);
    assertThat(takeover.getId().getTag("cause")).isEqualTo("unreachable");
    assertThat(takeover.getId().getTag("previous_zone")).isEqualTo("same");
    assertThat(takeover.count()).isOne();
    assertThat(takeover.totalTime(TimeUnit.SECONDS)).isEqualTo(3);
  }

  @Test
  void shouldMeasureTheTakeoverOfAFailedRegion() {
    // given
    tick(e1, e2, w1, w2);

    // when - the east region fails; the west region claims once the lease ran out
    advance(CONFIG.renewInterval());
    tick(w1, w2);
    advance(CONFIG.leaseDuration());
    tick(w1, w2);

    // then - the lease stood still since the first read, before the region failed
    final var takeover = w1.takeover(1);
    assertThat(takeover.getId().getTag("cause")).isEqualTo("expired");
    assertThat(takeover.getId().getTag("previous_zone")).isEqualTo("other");
    assertThat(takeover.totalTime(TimeUnit.SECONDS)).isEqualTo(12);
  }

  @Test
  void shouldTellAnOldLeaderThatItWasTakenOverWhenItReturns() {
    // given - e1 is paused for longer than the lease, so the west region claims its partitions
    tick(e1, e2, w1, w2);
    for (int round = 0; round < 5; round++) {
      advance(CONFIG.renewInterval());
      tick(e2, w1, w2);
    }
    assertThat(w1.elector.leading()).containsOnlyKeys(1, 3);

    // when - e1 resumes; it renews before it checks how long ago its last renewal was
    e1.elector.tick();

    // then
    assertThat(e1.events).contains("not leading 1 TAKEN_OVER", "not leading 3 TAKEN_OVER");
  }

  @Test
  void shouldStartTheWaitForTheLeaseAgainAfterAFailedRead() {
    // given - the east region fails, and the west region saw its rows once
    tick(e1, e2, w1, w2);

    // when - the west region cannot read for longer than the lease, then can again
    store.failReads(true);
    for (int round = 0; round < 5; round++) {
      advance(CONFIG.renewInterval());
      tick(w1, w2);
    }
    store.failReads(false);
    tick(w1, w2);
    final var afterTheFirstRead = Map.copyOf(w1.elector.leading());
    advance(CONFIG.leaseDuration());
    tick(w1, w2);

    // then - errors of the database did not count as time without a renewal
    assertThat(afterTheFirstRead).isEmpty();
    assertThat(w1.elector.leading()).containsOnlyKeys(1, 3);
  }

  @Test
  void shouldLetAnotherRegionClaimAfterTheLeaseWhenARegionShutsDown() {
    // given
    tick(e1, e2, w1, w2);

    // when - the whole east region shuts down
    eastAlive.clear();
    e1.elector.close();
    e2.elector.close();
    tick(w1, w2);
    final var atOnce = Map.copyOf(w1.elector.leading());
    advance(CONFIG.leaseDuration());
    tick(w1, w2);

    // then
    assertThat(e1.events).contains("not leading 1 RELEASED", "not leading 3 RELEASED");
    assertThat(atOnce).isEmpty();
    assertThat(w1.elector.leading()).containsOnlyKeys(1, 3);
    assertThat(w2.elector.leading()).containsOnlyKeys(2, 4);
  }

  @Test
  void shouldHandAPartitionToTheNextNodeOfTheRegionOnStepDown() {
    // given
    tick(e1, e2, w1, w2);

    // when
    e1.elector.stepDownNow(1);
    tick(e1, e2, w1, w2);

    // then
    assertThat(e1.events).contains("not leading 1 RELEASED");
    assertThat(e1.elector.leading()).containsOnlyKeys(3);
    assertThat(e2.elector.leading())
        .containsOnly(Map.entry(1, 2L), Map.entry(2, 1L), Map.entry(4, 1L));
    assertThat(w1.elector.leading()).isEmpty();
  }

  @Test
  void shouldKeepLeadingWhenTheStepDownCannotReleaseTheLease() {
    // given
    tick(e1, e2, w1, w2);
    store.failWritesOf("e1");

    // when
    final var stepDown = e1.elector.stepDown(1);

    // then - the test scheduler does not run the task, so run it here
    assertThat(stepDown).isNotDone();
    assertThatThrownBy(() -> e1.elector.stepDownNow(1)).isInstanceOf(MailboxStoreException.class);
    assertThat(e1.elector.leading()).containsKey(1);
  }

  @Test
  void shouldGiveOnlyOneOfTwoRegionsThePartitionWhenBothClaim() {
    // given - both regions read the rows before either claims
    final var rows = new InMemoryLeaseStore();
    final var raced = new RacingStore(rows);
    final var east = new Node("e1", "east", List.of("e1"), raced);
    final var west = new Node("w1", "west", List.of("w1"), raced);

    // when
    east.elector.tick();
    west.elector.tick();

    // then
    assertThat(rows.findLeases()).extracting(PartitionLease::term).containsOnly(1L);
    assertThat(east.elector.leading().size() + west.elector.leading().size()).isEqualTo(4);
  }

  private void tick(final Node... nodes) {
    for (final var node : nodes) {
      node.elector.tick();
    }
  }

  private void advance(final Duration duration) {
    clock.addAndGet(duration.toNanos());
  }

  private final class Node {
    private final List<String> events = new CopyOnWriteArrayList<>();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LeaderElector elector;

    private Node(final String id, final String zone, final List<String> alive) {
      this(id, zone, alive, store);
    }

    private Node(
        final String id, final String zone, final List<String> alive, final LeaseStore store) {
      elector =
          new LeaderElector(
              store,
              CONFIG,
              id,
              zone,
              PARTITIONS,
              () -> alive,
              new LeadershipListener() {
                @Override
                public void onLeading(final int partitionId, final long term) {
                  events.add("leading " + partitionId);
                }

                @Override
                public void onNotLeading(
                    final int partitionId, final long term, final Reason reason) {
                  events.add("not leading " + partitionId + " " + reason);
                }
              },
              (task, delay) -> {},
              clock::get,
              registry);
    }

    private double gauge(final String name, final int partitionId) {
      return registry.get(name).tag("partition", String.valueOf(partitionId)).gauge().value();
    }

    private double transitions(final int partitionId, final String event) {
      return registry
          .get(LeadershipMetrics.TRANSITIONS)
          .tag("partition", String.valueOf(partitionId))
          .tag("event", event)
          .counter()
          .count();
    }

    private Timer takeover(final int partitionId) {
      return registry
          .get(LeadershipMetrics.TAKEOVER_DELAY)
          .tag("partition", String.valueOf(partitionId))
          .timer();
    }
  }

  /** Passes calls to a store, and fails some of them when a test asks. */
  private static final class FlakyStore implements LeaseStore {
    private final LeaseStore delegate;
    private final Set<String> failingWriters = ConcurrentHashMap.newKeySet();
    private volatile boolean failReads;

    private FlakyStore(final LeaseStore delegate) {
      this.delegate = delegate;
    }

    void failWritesOf(final String nodeId) {
      failingWriters.add(nodeId);
    }

    void failReads(final boolean fail) {
      failReads = fail;
    }

    @Override
    public Optional<PartitionLease> claim(
        final int partitionId,
        final String nodeId,
        final String zone,
        final long observedTerm,
        final long observedCounter) {
      failIfWritesFail(nodeId);
      return delegate.claim(partitionId, nodeId, zone, observedTerm, observedCounter);
    }

    @Override
    public Set<Integer> renew(final String nodeId, final Map<Integer, Long> termsByPartition) {
      failIfWritesFail(nodeId);
      return delegate.renew(nodeId, termsByPartition);
    }

    @Override
    public boolean release(final int partitionId, final String nodeId, final long term) {
      failIfWritesFail(nodeId);
      return delegate.release(partitionId, nodeId, term);
    }

    @Override
    public Optional<PartitionLease> findLease(final int partitionId) {
      return delegate.findLease(partitionId);
    }

    private void failIfWritesFail(final String nodeId) {
      if (failingWriters.contains(nodeId)) {
        throw new MailboxStoreException("unreachable", new RuntimeException());
      }
    }

    @Override
    public List<PartitionLease> findLeases() {
      if (failReads) {
        throw new MailboxStoreException("unreachable", new RuntimeException());
      }
      return delegate.findLeases();
    }
  }

  /**
   * Hands every caller the rows as they were before the first claim, so that two nodes claim on the
   * same observation.
   */
  private static final class RacingStore implements LeaseStore {
    private final LeaseStore delegate;
    private final List<PartitionLease> firstRead = new ArrayList<>();
    private boolean read;

    private RacingStore(final LeaseStore delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<PartitionLease> claim(
        final int partitionId,
        final String nodeId,
        final String zone,
        final long observedTerm,
        final long observedCounter) {
      return delegate.claim(partitionId, nodeId, zone, observedTerm, observedCounter);
    }

    @Override
    public Set<Integer> renew(final String nodeId, final Map<Integer, Long> termsByPartition) {
      return delegate.renew(nodeId, termsByPartition);
    }

    @Override
    public boolean release(final int partitionId, final String nodeId, final long term) {
      return delegate.release(partitionId, nodeId, term);
    }

    @Override
    public Optional<PartitionLease> findLease(final int partitionId) {
      return delegate.findLease(partitionId);
    }

    @Override
    public List<PartitionLease> findLeases() {
      if (!read) {
        read = true;
        firstRead.addAll(delegate.findLeases());
      }
      return List.copyOf(firstRead);
    }
  }
}
