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
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference.Owner;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PhysicalTenantConfigReferenceTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");

  @Test
  void shouldStartAtTheInitialVersionWithNothingApplied() {
    // when
    final var reference = PhysicalTenantConfigReference.initial("a", Owner.API, "incarnation");

    // then
    assertThat(reference.version()).isEqualTo(PhysicalTenantConfigReference.INITIAL_VERSION);
    assertThat(reference.appliedVersion(MEMBER_0)).isZero();
    assertThat(reference.isAppliedBy(List.of(MEMBER_0))).isFalse();
  }

  @Test
  void shouldKeepOwnerIncarnationAndAppliedVersionsOnTheNextVersion() {
    // given
    final var reference =
        PhysicalTenantConfigReference.initial("a", Owner.FILE, "incarnation")
            .withApplied(MEMBER_0, 1);

    // when
    final var next = reference.next("b");

    // then
    assertThat(next.version()).isEqualTo(2);
    assertThat(next.sha256()).isEqualTo("b");
    assertThat(next.owner()).isEqualTo(Owner.FILE);
    assertThat(next.incarnationId()).isEqualTo("incarnation");
    assertThat(next.appliedVersion(MEMBER_0)).isEqualTo(1);
    assertThat(next.isAppliedBy(List.of(MEMBER_0))).isFalse();
  }

  @Test
  void shouldIgnoreAnAppliedVersionLowerThanTheKnownOne() {
    // given
    final var reference =
        PhysicalTenantConfigReference.initial("a", Owner.API, "incarnation")
            .next("b")
            .withApplied(MEMBER_0, 2);

    // when
    final var updated = reference.withApplied(MEMBER_0, 1);

    // then
    assertThat(updated).isSameAs(reference);
  }

  @Test
  void shouldBeAppliedOnceEveryMemberRunsTheCurrentVersion() {
    // given
    final var reference =
        PhysicalTenantConfigReference.initial("a", Owner.API, "incarnation")
            .withApplied(MEMBER_0, 1);

    // when
    final var everywhere = reference.withApplied(MEMBER_1, 1);

    // then
    assertThat(reference.isAppliedBy(List.of(MEMBER_0, MEMBER_1))).isFalse();
    assertThat(everywhere.isAppliedBy(List.of(MEMBER_0, MEMBER_1))).isTrue();
  }

  @Test
  void shouldMergeToTheHigherVersionAndTheHigherAppliedVersionOfEachMember() {
    // given
    final var initial = PhysicalTenantConfigReference.initial("a", Owner.API, "incarnation");
    final var newerOnCoordinator = initial.next("b").withApplied(MEMBER_0, 2);
    final var appliedElsewhere = initial.withApplied(MEMBER_1, 1);

    // when
    final var merged =
        PhysicalTenantConfigReference.merge(
            Optional.of(appliedElsewhere), Optional.of(newerOnCoordinator));
    final var mergedOtherWay =
        PhysicalTenantConfigReference.merge(
            Optional.of(newerOnCoordinator), Optional.of(appliedElsewhere));

    // then
    assertThat(merged).isEqualTo(mergedOtherWay);
    assertThat(merged)
        .hasValueSatisfying(
            reference -> {
              assertThat(reference.version()).isEqualTo(2);
              assertThat(reference.sha256()).isEqualTo("b");
              assertThat(reference.appliedVersion(MEMBER_0)).isEqualTo(2);
              assertThat(reference.appliedVersion(MEMBER_1)).isEqualTo(1);
            });
  }

  @Test
  void shouldKeepAReferenceWhenMergedWithNone() {
    // given
    final var reference = PhysicalTenantConfigReference.initial("a", Owner.API, "incarnation");

    // when
    final var merged =
        PhysicalTenantConfigReference.merge(Optional.empty(), Optional.of(reference));

    // then
    assertThat(merged).contains(reference);
  }
}
