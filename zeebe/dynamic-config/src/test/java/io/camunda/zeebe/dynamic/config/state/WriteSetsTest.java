/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The write set is what tells the coordinator whether two operations without a dependency between
 * them are safe to run together. Getting an entry wrong is silent, so the cases that are easy to
 * get wrong are pinned here.
 */
final class WriteSetsTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");

  @Test
  void shouldWriteTheRemovedMemberNotTheActingOne() {
    // given — MemberRemove dispatches to MemberLeaveApplier(op.memberToRemove(), ...), so the
    // broker applying it and the entry it writes are different members
    final var operation = new MemberRemoveOperation(MEMBER_0, MEMBER_1);

    // when / then — deriving the write set from memberId() would claim member 0 and let this run
    // concurrently with something else writing member 1
    assertThat(WriteSets.of(operation).members()).containsExactly(MEMBER_1);
  }

  @Test
  void shouldTreatForceReconfigureAsConflictingWithEverything() {
    // given — the applier loops group.members().keySet() and removes the partition from every
    // member outside the target replication group, so the written set depends on live state and
    // cannot be derived from the operation
    final var operation =
        new PartitionForceReconfigureOperation(MEMBER_0, 1, new TreeSet<>(Set.of(MEMBER_0)));

    // when / then — conservatively conflicts with all, which serialises this recovery path
    final var writeSet = WriteSets.of(operation);
    assertThat(writeSet.isDisjointFrom(WriteSets.of(new MemberJoinOperation(MEMBER_1)))).isFalse();
    assertThat(writeSet.isDisjointFrom(writeSet)).isFalse();
  }

  @Test
  void shouldReportTheOperationsThatWriteNothing() {
    // given — six operations exist purely for their side effects and touch no configuration state.
    // Disjointness therefore says nothing useful about them, and only a declared dependency can
    // order them. This is the core reason dependencies are declared rather than inferred.
    final var writeNothing =
        Set.of(
            new PreScalingOperation(MEMBER_0, new TreeSet<>(Set.of(MEMBER_0))),
            new PostScalingOperation(MEMBER_0, new TreeSet<>(Set.of(MEMBER_0))),
            new DeleteHistoryOperation(MEMBER_0),
            new AwaitRelocationCompletion(MEMBER_0, 2, new TreeSet<>(Set.of(1))),
            new PartitionPreRestoreOperation(MEMBER_0, 1),
            new PartitionRestoreOperation(MEMBER_0, 1, new TreeSet<>(Set.of(1L))));

    // when / then
    assertThat(writeNothing)
        .allSatisfy(operation -> assertThat(WriteSets.of(operation).writesNothing()).isTrue());
  }

  @Test
  void shouldSeparatePartitionsOfTheSameMember() {
    // given — this is what makes partition parallelism fall out with no extra machinery
    final var first = WriteSets.of(new PartitionJoinOperation(MEMBER_0, 1, 1));
    final var second = WriteSets.of(new PartitionJoinOperation(MEMBER_0, 2, 1));
    final var sameParition = WriteSets.of(new PartitionJoinOperation(MEMBER_0, 1, 2));

    // when / then
    assertThat(first.isDisjointFrom(second)).isTrue();
    assertThat(first.isDisjointFrom(sameParition)).isFalse();
  }

  @Test
  void shouldTreatAMemberWideWriteAsConflictingWithItsOwnPartitions() {
    // given — AwaitModeChange writes several partitions of its own member, so it is member-scoped;
    // treating it as partition-scoped would let it race with a partition operation on that member
    final var memberWide = WriteSets.of(new AwaitModeChangeOperation(MEMBER_0, Mode.PROCESSING));
    final var onePartition = WriteSets.of(new PartitionJoinOperation(MEMBER_0, 1, 1));
    final var otherMember = WriteSets.of(new PartitionJoinOperation(MEMBER_1, 1, 1));

    // when / then
    assertThat(memberWide.isDisjointFrom(onePartition)).isFalse();
    assertThat(memberWide.isDisjointFrom(otherMember)).isTrue();
  }

  @Test
  void shouldTreatSubConfigurationWritesAsConflictingWithEverything() {
    // given
    final var subConfig = WriteSets.of(new UpdateIncarnationNumberOperation(MEMBER_0));

    // when / then — including with another sub-configuration write
    assertThat(subConfig.isDisjointFrom(WriteSets.of(new PartitionJoinOperation(MEMBER_1, 1, 1))))
        .isFalse();
    assertThat(subConfig.isDisjointFrom(subConfig)).isFalse();
  }
}
