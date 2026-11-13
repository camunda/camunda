/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class TransferOrderTest {

  private static final String GROUP = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");
  private static final MemberId MEMBER_3 = MemberId.from("3");
  private static final MemberId MEMBER_4 = MemberId.from("4");

  @Test
  void shouldDrainABrokerBeforeMovingLeadershipOntoIt() {
    // given
    final var planned =
        List.of(
            PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_2),
            PartitionRebalance.pending(GROUP, 2, MEMBER_2, MEMBER_3));

    // when
    final var sequence = transferSequence(planned);

    // then
    assertThat(sequence).containsExactly(1, 0);
  }

  @Test
  void shouldRelieveTheMostOverloadedBrokerFirst() {
    // given
    final var planned =
        List.of(
            PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_3),
            PartitionRebalance.pending(GROUP, 2, MEMBER_2, MEMBER_4),
            PartitionRebalance.pending(GROUP, 3, MEMBER_2, MEMBER_4));

    // when
    final var sequence = transferSequence(planned);

    // then
    assertThat(sequence).first().isEqualTo(1);
  }

  @Test
  void shouldTransferToTheBrokerLeadingTheFewestPartitionsFirst() {
    // given
    final var planned =
        List.of(
            PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_2),
            PartitionRebalance.pending(GROUP, 2, MEMBER_1, MEMBER_3),
            PartitionRebalance.alreadyLeader(GROUP, 3, MEMBER_2),
            PartitionRebalance.alreadyLeader(GROUP, 4, MEMBER_2),
            PartitionRebalance.alreadyLeader(GROUP, 5, MEMBER_3));

    // when
    final var sequence = transferSequence(planned);

    // then
    assertThat(sequence).containsExactly(1, 0);
  }

  @Test
  void shouldCountLeadershipAcrossPhysicalTenants() {
    // given
    final var planned =
        List.of(
            PartitionRebalance.pending("tenant-a", 1, MEMBER_1, MEMBER_2),
            PartitionRebalance.pending("tenant-b", 1, MEMBER_2, MEMBER_3));

    // when
    final var sequence = transferSequence(planned);

    // then
    assertThat(sequence).containsExactly(1, 0);
  }

  @Test
  void shouldTransferPartitionsWithoutALeaderLast() {
    // given
    final var planned =
        List.of(
            PartitionRebalance.pending(GROUP, 1, null, MEMBER_2),
            PartitionRebalance.pending(GROUP, 2, MEMBER_1, MEMBER_2));

    // when
    final var sequence = transferSequence(planned);

    // then
    assertThat(sequence).containsExactly(1, 0);
  }

  @Test
  void shouldTransferTheLowestPartitionIdFirstBetweenOtherwiseEqualTransfers() {
    // given
    final var planned =
        List.of(
            PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_2),
            PartitionRebalance.pending(GROUP, 2, MEMBER_1, MEMBER_2));

    // when
    final var sequence = transferSequence(planned);

    // then
    assertThat(sequence).containsExactly(0, 1);
  }

  @Test
  void shouldChooseByWhereLeadershipIsNowRatherThanWhereItWasPlanned() {
    // given
    final var planned =
        List.of(
            PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_3),
            PartitionRebalance.pending(GROUP, 2, MEMBER_2, MEMBER_4),
            PartitionRebalance.pending(GROUP, 3, MEMBER_2, MEMBER_4));
    final var current = new ArrayList<>(planned);

    // when
    current.set(2, current.get(2).leaderChanged(MEMBER_1));

    // then
    assertThat(TransferOrder.next(current)).hasValue(0);
  }

  @Test
  void shouldHaveNothingToTransferOnceNoPartitionIsPending() {
    // given
    final var planned = List.of(PartitionRebalance.alreadyLeader(GROUP, 1, MEMBER_1));

    // when
    final var next = TransferOrder.next(planned);

    // then
    assertThat(next).isEmpty();
  }

  /** The indexes of the planned partitions in the order they are transferred, if all succeed. */
  private static List<Integer> transferSequence(final List<PartitionRebalance> planned) {
    final var current = new ArrayList<>(planned);
    final List<Integer> sequence = new ArrayList<>();
    for (var next = TransferOrder.next(current);
        next.isPresent();
        next = TransferOrder.next(current)) {
      final var index = next.getAsInt();
      sequence.add(index);
      current.set(index, current.get(index).transferred());
    }
    return sequence;
  }
}
