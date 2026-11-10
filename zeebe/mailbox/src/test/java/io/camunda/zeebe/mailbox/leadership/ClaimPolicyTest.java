/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.mailbox.api.leadership.PartitionLease;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ClaimPolicyTest {

  private static final String EAST = "east";
  private static final String WEST = "west";

  // with the live nodes [e1, e2], partition 1 goes to e1 first and partition 2 to e2
  private final ClaimPolicy e1 = new ClaimPolicy("e1", EAST);
  private final ClaimPolicy e2 = new ClaimPolicy("e2", EAST);
  private final ClusterView bothAlive = () -> List.of("e2", "e1");
  private final ClusterView onlyE2Alive = () -> List.of("e2");

  @Test
  void shouldLetOnlyTheFirstNodeInThePriorityOrderClaimAFreePartition() {
    // then
    assertThat(e1.shouldClaim(1, null, false, bothAlive)).isTrue();
    assertThat(e2.shouldClaim(1, null, false, bothAlive)).isFalse();
    assertThat(e2.shouldClaim(2, null, false, bothAlive)).isTrue();
  }

  @Test
  void shouldClaimAtOnceWhenGossipNoLongerSeesALeaderOfTheSameRegion() {
    // given
    final var lease = new PartitionLease(2, "e1", EAST, 1, 7, false);

    // then - no wait for the lease
    assertThat(e2.shouldClaim(2, lease, false, onlyE2Alive)).isTrue();
  }

  @Test
  void shouldNotClaimFromALiveLeaderOfTheSameRegionThatStillRenews() {
    // given
    final var lease = new PartitionLease(2, "e1", EAST, 1, 7, false);

    // then
    assertThat(e2.shouldClaim(2, lease, false, bothAlive)).isFalse();
  }

  @Test
  void shouldClaimFromALiveLeaderOfTheSameRegionWhoseLeaseRanOut() {
    // given - the leader is alive but lost the database, and fenced itself
    final var lease = new PartitionLease(2, "e1", EAST, 1, 7, false);

    // then
    assertThat(e2.shouldClaim(2, lease, true, bothAlive)).isTrue();
  }

  @Test
  void shouldWaitForTheLeaseOfALeaderInAnotherRegion() {
    // given
    final var lease = new PartitionLease(1, "w1", WEST, 3, 7, false);

    // then
    assertThat(e1.shouldClaim(1, lease, false, bothAlive)).isFalse();
    assertThat(e1.shouldClaim(1, lease, true, bothAlive)).isTrue();
  }

  @Test
  void shouldNotClaimARunOutLeaseWhenAnotherNodeIsFirstInThePriorityOrder() {
    // given
    final var lease = new PartitionLease(1, "w1", WEST, 3, 7, false);

    // then
    assertThat(e2.shouldClaim(1, lease, true, bothAlive)).isFalse();
  }

  @Test
  void shouldClaimAReleasedPartitionAtOnce() {
    // given
    final var lease = new PartitionLease(1, "w1", WEST, 3, 8, true);

    // then
    assertThat(e1.shouldClaim(1, lease, false, bothAlive)).isTrue();
  }

  @Test
  void shouldTakeBackItsOwnLeaseThatItNoLongerHolds() {
    // given - e2 restarted, and the other nodes see it alive, so they do not claim
    final var lease = new PartitionLease(1, "e2", EAST, 3, 7, false);

    // then
    assertThat(e2.shouldClaim(1, lease, false, bothAlive)).isTrue();
    assertThat(e1.shouldClaim(1, lease, false, bothAlive)).isFalse();
  }
}
