/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.fact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class ProcessInstanceExecutionTimeFactCodecTest {

  private final ProcessInstanceExecutionTimeFactCodec codec =
      new ProcessInstanceExecutionTimeFactCodec();

  @Test
  void shouldRoundTripCompletedFactWithVariables() {
    // given
    final ProcessInstanceExecutionTimeFact fact =
        new ProcessInstanceExecutionTimeFact(
            123L,
            77L,
            "order",
            3,
            "<default>",
            1000L,
            1500L,
            500L,
            true,
            1,
            11L,
            java.util.Map.of("region", "EU", "priority", "high"));

    // when / then — variables survive the round-trip
    assertThat(codec.deserialize(codec.serialize(fact))).isEqualTo(fact);
  }

  @Test
  void shouldRoundTripTerminatedFactWithNoVariables() {
    // given
    final ProcessInstanceExecutionTimeFact fact =
        new ProcessInstanceExecutionTimeFact(
            9L, 5L, "payment", 1, "tenant-x", 0L, 200L, 200L, false, 2, 99L, java.util.Map.of());

    // when / then
    assertThat(codec.deserialize(codec.serialize(fact))).isEqualTo(fact);
  }
}
