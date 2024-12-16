/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.scaling.redistribution.RedistributionStage.Deployments;
import io.camunda.zeebe.engine.scaling.redistribution.RedistributionStage.Done;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class RedistributionStateTest {
  private MutableProcessingState processingState;
  private MutableRedistributionState redistributionState;

  @BeforeEach
  public void setup() {
    redistributionState = processingState.getRedistributionState();
  }

  @Test
  void shouldInitializeState() {
    // given
    final var stage = new Deployments();
    final var progress = new RedistributionProgress().claimDeploymentKey(123);

    // when
    redistributionState.initializeState(stage, progress);

    // then
    assertThat(redistributionState.getStage()).isEqualTo(stage);
    assertThat(redistributionState.getProgress()).isEqualTo(progress);
  }

  @Test
  void shouldUpdateState() {
    // given
    final var initialStage = new Done();
    final var initialProgress = new RedistributionProgress();
    redistributionState.initializeState(initialStage, initialProgress);

    final var updatedStage = RedistributionStage.nextStage(initialStage);
    final var updatedProgress = initialProgress.claimDeploymentKey(123);

    // when
    redistributionState.updateState(updatedStage, updatedProgress);

    // then
    assertThat(redistributionState.getStage()).isEqualTo(updatedStage);
    assertThat(redistributionState.getProgress()).isEqualTo(updatedProgress);
  }

  @Test
  void shouldClearState() {
    // given
    final var stage = new Done();
    final var progress = new RedistributionProgress().claimDeploymentKey(123);
    redistributionState.initializeState(stage, progress);

    // when
    redistributionState.clearState();

    // then
    assertThat(redistributionState.getStage()).isEqualTo(new Done());
    assertThat(redistributionState.getProgress()).isEqualTo(new RedistributionProgress());
  }
}
