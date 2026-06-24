/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.RoutingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbRoutingStateTest {
  @SuppressWarnings("unused")
  private MutableProcessingState processingState;

  @Test
  void shouldDefaultToNotInitialized() {
    // given
    assertThat(processingState.getRoutingState().isInitialized()).isFalse();
  }

  @Test
  void shouldInitializeRoutingInfo() {
    // given
    final var routingState = processingState.getRoutingState();
    final int partitionCount = 3;

    // when
    routingState.initializeRoutingInfo(partitionCount);

    // then
    assertThat(routingState.isInitialized()).isTrue();
    assertThat(routingState.currentPartitions()).containsExactlyInAnyOrder(1, 2, 3);
    assertThat(routingState.desiredPartitions()).containsExactlyInAnyOrder(1, 2, 3);
    assertThat(routingState.messageCorrelation())
        .isEqualTo(new RoutingState.MessageCorrelation.HashMod(3));
    assertThat(routingState.scalingStartedAt(3)).isZero();
  }

  @Test
  void shouldFillScalingStartedAtCorrectly() {
    // given
    final var routingState = processingState.getRoutingState();
    routingState.initializeRoutingInfo(1);
    final long eventKey = 1231923L;

    // when
    routingState.setDesiredPartitions(Set.of(1, 2, 3), eventKey);

    // then
    assertThat(routingState.scalingStartedAt(1)).isEqualTo(0L);
    assertThat(routingState.scalingStartedAt(3)).isEqualTo(eventKey);
    assertThat(routingState.scalingStartedAt(2)).isEqualTo(-1L);
  }

  @Test
  void shouldArriveAtDesiredState() {
    // given
    final var routingState = processingState.getRoutingState();
    routingState.initializeRoutingInfo(1);
    assertThat(routingState.currentPartitions()).containsExactlyInAnyOrder(1);
    final long eventKey = 1231923L;

    // when
    routingState.setDesiredPartitions(Set.of(1, 2, 3), eventKey);

    // then
    assertThat(routingState.currentPartitions()).containsExactlyInAnyOrder(1);
    assertThat(routingState.messageCorrelation())
        .isEqualTo(new RoutingState.MessageCorrelation.HashMod(1));

    // when
    routingState.activatePartition(2);
    routingState.activatePartition(3);

    // then
    assertThat(routingState.currentPartitions()).containsExactlyInAnyOrder(1, 2, 3);
    assertThat(routingState.messageCorrelation())
        .isEqualTo(new RoutingState.MessageCorrelation.HashMod(1));
  }
}
