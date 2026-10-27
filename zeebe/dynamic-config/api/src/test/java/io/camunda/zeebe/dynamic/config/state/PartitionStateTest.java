/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class PartitionStateTest {

  @Test
  void shouldTransitionToRecovering() {
    // given
    final var config = DynamicPartitionConfig.init();
    final var partitionState = PartitionState.active(3, config);

    // when
    final var recovering = partitionState.toRecovering();

    // then
    assertThat(recovering.state()).isEqualTo(PartitionState.State.RECOVERING);
    assertThat(recovering.priority()).isEqualTo(3);
    assertThat(recovering.config()).isEqualTo(config);
  }
}
