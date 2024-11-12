/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

final class PartitionUtilTest {

  @Test
  void shouldReturnAllPartitions() {
    // when
    final var partitions = PartitionUtil.allPartitions(5);

    // then
    assertThat(partitions).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
  }

  @Test
  void shouldFailForTooManyPartitions() {
    assertThatCode(() -> PartitionUtil.allPartitions(10_000))
        .hasMessage("Partition id 10000 must be <= 8192");
  }

  @Test
  void shouldFailForTooFewPartitions() {
    assertThatCode(() -> PartitionUtil.allPartitions(0)).hasMessage("Partition id 0 must be >= 1");
  }
}
