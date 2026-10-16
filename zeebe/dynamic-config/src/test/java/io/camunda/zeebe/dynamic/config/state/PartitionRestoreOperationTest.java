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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import java.util.List;
import java.util.OptionalLong;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

final class PartitionRestoreOperationTest {

  @Test
  void shouldCarryTheRestoreItWasCreatedWith() {
    // given - a restore that ends before its last backup's checkpoint
    final var restore =
        new PartitionRestore(new TreeSet<>(List.of(10L, 20L)), OptionalLong.of(150L));

    // when
    final var operation = new PartitionRestoreOperation(MemberId.from("1"), 1, restore);

    // then
    assertThat(operation.restore()).isEqualTo(restore);
  }
}
