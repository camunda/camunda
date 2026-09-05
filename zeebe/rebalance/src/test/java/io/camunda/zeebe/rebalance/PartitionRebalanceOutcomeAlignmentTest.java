/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.LeadershipTransferResult;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * {@link PartitionRebalanceOutcome} is the rebalance-domain representation of {@link
 * LeadershipTransferResult}: every Raft transfer result must have an identically named outcome, so
 * that a new result added to Raft cannot silently collapse into nothing on the rebalance side.
 */
final class PartitionRebalanceOutcomeAlignmentTest {

  @Test
  void shouldHaveAnIdenticallyNamedOutcomeForEveryTransferResult() {
    // given
    final var transferResultNames = namesOf(LeadershipTransferResult.class);
    final var outcomeNames = namesOf(PartitionRebalanceOutcome.class);

    // then
    assertThat(transferResultNames).allMatch(outcomeNames::contains);
  }

  @Test
  void shouldOnlyAddCoordinatorOriginatedOutcomesBeyondTransferResults() {
    // given
    final var transferResultNames = namesOf(LeadershipTransferResult.class);
    final var outcomeNames = namesOf(PartitionRebalanceOutcome.class);

    // when
    final var additionalOutcomeNames =
        outcomeNames.stream()
            .filter(name -> !transferResultNames.contains(name))
            .collect(Collectors.toSet());

    // then
    assertThat(additionalOutcomeNames)
        .containsExactlyInAnyOrder(
            "NO_LEADER", "NO_RESPONSE", "CANCELLED", "PHYSICAL_TENANT_DISABLED");
  }

  private static Set<String> namesOf(final Class<? extends Enum<?>> enumType) {
    return Arrays.stream(enumType.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
  }
}
