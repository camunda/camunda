/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api.leadership;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour that every {@link LeaseStore} must have. An implementation extends this class and
 * provides a store.
 *
 * <p>Each test uses its own partitions and node ids, so the tests can share one database.
 */
public abstract class LeaseStoreContractTest {

  private static final String EAST = "east";
  private static final String WEST = "west";

  protected LeaseStore store;
  private int partition;
  private String nodeA;
  private String nodeB;

  /** Creates the store under test. It is called before each test. */
  protected abstract LeaseStore createStore();

  @BeforeEach
  void setUpStore() {
    store = createStore();
    partition = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE - 1);
    nodeA = "node-a-" + UUID.randomUUID();
    nodeB = "node-b-" + UUID.randomUUID();
  }

  @Test
  void shouldClaimAPartitionThatHasNoLease() {
    // when
    final var lease = store.claim(partition, nodeA, EAST, LeaseStore.NO_LEASE, 0);

    // then
    assertThat(lease).contains(new PartitionLease(partition, nodeA, EAST, 1, 0, false));
    assertThat(store.findLease(partition)).isEqualTo(lease);
  }

  @Test
  void shouldLetOnlyOneOfTwoClaimsOnTheSameObservationWin() {
    // given
    store.claim(partition, nodeA, EAST, LeaseStore.NO_LEASE, 0);

    // when
    final var second = store.claim(partition, nodeB, WEST, LeaseStore.NO_LEASE, 0);

    // then
    assertThat(second).isEmpty();
    assertThat(store.findLease(partition))
        .get()
        .extracting(PartitionLease::leaderId)
        .isEqualTo(nodeA);
  }

  @Test
  void shouldTakeOverWithTheObservedTermAndCounter() {
    // given
    store.claim(partition, nodeA, EAST, LeaseStore.NO_LEASE, 0);
    store.renew(nodeA, Map.of(partition, 1L));

    // when
    final var lease = store.claim(partition, nodeB, WEST, 1, 1);

    // then
    assertThat(lease).contains(new PartitionLease(partition, nodeB, WEST, 2, 0, false));
  }

  @Test
  void shouldNotTakeOverWhenTheLeaderRenewedSinceTheObservation() {
    // given
    store.claim(partition, nodeA, EAST, LeaseStore.NO_LEASE, 0);
    store.renew(nodeA, Map.of(partition, 1L));

    // when - the observation is from before the renewal
    final var lease = store.claim(partition, nodeB, WEST, 1, 0);

    // then
    assertThat(lease).isEmpty();
    assertThat(store.findLease(partition))
        .get()
        .extracting(PartitionLease::leaderId)
        .isEqualTo(nodeA);
  }

  @Test
  void shouldNotTakeOverWithAnOldTerm() {
    // given
    store.claim(partition, nodeA, EAST, LeaseStore.NO_LEASE, 0);
    store.claim(partition, nodeB, WEST, 1, 0);

    // when
    final var lease = store.claim(partition, nodeA, EAST, 1, 0);

    // then
    assertThat(lease).isEmpty();
  }

  @Test
  void shouldRenewTheLeasesOfANodeTogether() {
    // given
    final var other = partition + 1;
    store.claim(partition, nodeA, EAST, LeaseStore.NO_LEASE, 0);
    store.claim(other, nodeA, EAST, LeaseStore.NO_LEASE, 0);

    // when
    final var renewed = store.renew(nodeA, Map.of(partition, 1L, other, 1L));

    // then
    assertThat(renewed).containsExactlyInAnyOrder(partition, other);
    assertThat(store.findLease(partition))
        .get()
        .extracting(PartitionLease::renewalCounter)
        .isEqualTo(1L);
    assertThat(store.findLease(other))
        .get()
        .extracting(PartitionLease::renewalCounter)
        .isEqualTo(1L);
  }

  @Test
  void shouldNotRenewALeaseThatAnotherNodeTookOver() {
    // given
    store.claim(partition, nodeA, EAST, LeaseStore.NO_LEASE, 0);
    store.claim(partition, nodeB, WEST, 1, 0);

    // when
    final var renewed = store.renew(nodeA, Map.of(partition, 1L));

    // then
    assertThat(renewed).isEmpty();
    assertThat(store.findLease(partition))
        .contains(new PartitionLease(partition, nodeB, WEST, 2, 0, false));
  }

  @Test
  void shouldNotRenewAPartitionWithoutALease() {
    // when
    final var renewed = store.renew(nodeA, Map.of(partition, 1L));

    // then
    assertThat(renewed).isEmpty();
    assertThat(store.findLease(partition)).isEmpty();
  }

  @Test
  void shouldReleaseALeaseSoThatAnotherNodeCanClaimItAtOnce() {
    // given
    store.claim(partition, nodeA, EAST, LeaseStore.NO_LEASE, 0);

    // when
    final var released = store.release(partition, nodeA, 1);

    // then
    assertThat(released).isTrue();
    final var lease = store.findLease(partition).orElseThrow();
    assertThat(lease.released()).isTrue();
    assertThat(lease.renewalCounter()).isEqualTo(1);
    assertThat(store.renew(nodeA, Map.of(partition, 1L))).isEmpty();
    assertThat(store.claim(partition, nodeB, WEST, lease.term(), lease.renewalCounter()))
        .contains(new PartitionLease(partition, nodeB, WEST, 2, 0, false));
  }

  @Test
  void shouldNotReleaseALeaseOfAnotherNodeOrTerm() {
    // given
    store.claim(partition, nodeA, EAST, LeaseStore.NO_LEASE, 0);

    // when / then
    assertThat(store.release(partition, nodeB, 1)).isFalse();
    assertThat(store.release(partition, nodeA, 2)).isFalse();
    assertThat(store.findLease(partition))
        .get()
        .extracting(PartitionLease::released)
        .isEqualTo(false);
  }

  @Test
  void shouldFindAllLeases() {
    // given
    final var other = partition + 1;
    store.claim(partition, nodeA, EAST, LeaseStore.NO_LEASE, 0);
    store.claim(other, nodeB, WEST, LeaseStore.NO_LEASE, 0);

    // when
    final var leases = store.findLeases();

    // then
    assertThat(leases)
        .contains(
            new PartitionLease(partition, nodeA, EAST, 1, 0, false),
            new PartitionLease(other, nodeB, WEST, 1, 0, false));
  }
}
