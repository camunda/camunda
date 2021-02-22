/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.state.mutable.MutableDeploymentState;
import io.zeebe.engine.util.ZeebeStateRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DeploymentStateTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private MutableDeploymentState deploymentState;

  @Before
  public void setUp() {
    final var zeebeState = stateRule.getZeebeState();
    deploymentState = zeebeState.getDeploymentState();
  }

  @Test
  public void shouldReturnFalseOnEmptyStateForHasPendingCheck() {
    // given

    // when
    final var hasPending = deploymentState.hasPendingDeploymentDistribution(10L);

    // then
    assertThat(hasPending).isFalse();
  }

  @Test
  public void shouldAddPendingDeployment() {
    // given
    final var deploymentKey = 10L;
    final var partition = 1;

    // when
    deploymentState.addPendingDeploymentDistribution(deploymentKey, partition);

    // then
    assertThat(deploymentState.hasPendingDeploymentDistribution(deploymentKey)).isTrue();
  }

  @Test
  public void shouldReturnFalseForDifferentPendingDeploymentOnHasPendingCheck() {
    // given
    final var deploymentKey = 10L;
    final var partition = 1;
    deploymentState.addPendingDeploymentDistribution(deploymentKey, partition);

    // when
    final var hasPending = deploymentState.hasPendingDeploymentDistribution(12L);

    // then
    assertThat(hasPending).isFalse();
  }

  @Test
  public void shouldRemovePendingDeployment() {
    // given
    final var deploymentKey = 10L;
    final var partition = 1;
    deploymentState.addPendingDeploymentDistribution(deploymentKey, partition);

    // when
    deploymentState.removePendingDeploymentDistribution(deploymentKey, partition);

    // then
    assertThat(deploymentState.hasPendingDeploymentDistribution(deploymentKey)).isFalse();
  }

  @Test
  public void shouldRemovePendingDeploymentIdempotent() {
    // given
    final var deploymentKey = 10L;
    final var partition = 1;
    deploymentState.addPendingDeploymentDistribution(deploymentKey, partition);
    deploymentState.removePendingDeploymentDistribution(deploymentKey, partition);

    // when
    deploymentState.removePendingDeploymentDistribution(deploymentKey, partition);

    // then
    assertThat(deploymentState.hasPendingDeploymentDistribution(deploymentKey)).isFalse();
  }

  @Test
  public void shouldReturnTrueForDifferentPendingDeploymentOnHasPendingCheck() {
    // given
    final var deploymentKey = 10L;
    final var partition = 1;
    deploymentState.addPendingDeploymentDistribution(deploymentKey, partition);
    deploymentState.addPendingDeploymentDistribution(12L, partition);
    deploymentState.removePendingDeploymentDistribution(deploymentKey, partition);

    // when
    final var hasPending = deploymentState.hasPendingDeploymentDistribution(12L);

    // then
    assertThat(hasPending).isTrue();
  }
}
