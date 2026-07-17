/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the loop-detection threshold and retry-cooldown arithmetic. The engine-level
 * behaviour (raising and resolving the incident) is covered by the {@code
 * LoopDetection*IncidentTest} integration tests; here we verify the pure decision logic in
 * isolation.
 */
final class BpmnLoopDetectionBehaviorTest {

  private static final long PROCESS_INSTANCE_KEY = 42L;
  private static final DirectBuffer ELEMENT_ID = BufferUtil.wrapString("mi-task");

  private final MutableElementInstanceState elementInstanceState =
      mock(MutableElementInstanceState.class);
  private final BpmnElementContext serviceTaskContext = mock(BpmnElementContext.class);

  @BeforeEach
  void setUp() {
    when(serviceTaskContext.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(serviceTaskContext.getElementId()).thenReturn(ELEMENT_ID);
    when(serviceTaskContext.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);
  }

  private BpmnLoopDetectionBehavior behavior(
      final int max, final Map<BpmnElementType, Integer> byType, final int cooldown) {
    return new BpmnLoopDetectionBehavior(elementInstanceState, max, byType, cooldown);
  }

  private void activationCount(final long count) {
    when(elementInstanceState.getElementActivationCounter(PROCESS_INSTANCE_KEY, ELEMENT_ID))
        .thenReturn(count);
  }

  private boolean regularRaisesAt(final BpmnLoopDetectionBehavior behavior, final long count) {
    activationCount(count);
    return behavior.checkActivationThreshold(serviceTaskContext).isLeft();
  }

  private boolean batchStopsAt(
      final BpmnLoopDetectionBehavior behavior, final long childActivationCount) {
    activationCount(childActivationCount);
    return behavior.isChildActivationThresholdExceeded(
        PROCESS_INSTANCE_KEY, ELEMENT_ID, BpmnElementType.SERVICE_TASK);
  }

  private boolean childRaisesAt(final BpmnLoopDetectionBehavior behavior, final long count) {
    activationCount(count);
    return behavior.checkMultiInstanceChildActivationThreshold(serviceTaskContext).isLeft();
  }

  // ---------------------------------------------------------------------------
  // checkActivationThreshold
  // ---------------------------------------------------------------------------

  @Test
  void shouldNotRaiseWithinThreshold() {
    final var behavior = behavior(3, Map.of(), 1);

    assertThat(regularRaisesAt(behavior, 1)).isFalse();
    assertThat(regularRaisesAt(behavior, 2)).isFalse();
    assertThat(regularRaisesAt(behavior, 3)).isFalse();
  }

  @Test
  void shouldRaiseOnFirstActivationBeyondThreshold() {
    final var behavior = behavior(3, Map.of(), 3);

    assertThat(regularRaisesAt(behavior, 4)).isTrue();
  }

  @Test
  void shouldThrottleRegularReRaisingByCooldown() {
    final var behavior = behavior(3, Map.of(), 3);

    // fires on the first breach (4), then every cooldown (3) activations after that
    assertThat(regularRaisesAt(behavior, 4)).isTrue();
    assertThat(regularRaisesAt(behavior, 5)).isFalse();
    assertThat(regularRaisesAt(behavior, 6)).isFalse();
    assertThat(regularRaisesAt(behavior, 7)).isTrue();
    assertThat(regularRaisesAt(behavior, 8)).isFalse();
    assertThat(regularRaisesAt(behavior, 9)).isFalse();
    assertThat(regularRaisesAt(behavior, 10)).isTrue();
  }

  @Test
  void shouldRaiseOnEveryActivationWhenCooldownIsOne() {
    final var behavior = behavior(3, Map.of(), 1);

    assertThat(regularRaisesAt(behavior, 4)).isTrue();
    assertThat(regularRaisesAt(behavior, 5)).isTrue();
    assertThat(regularRaisesAt(behavior, 6)).isTrue();
  }

  @Test
  void shouldRaiseOnEveryActivationWhenCooldownIsZero() {
    final var behavior = behavior(3, Map.of(), 0);

    assertThat(regularRaisesAt(behavior, 4)).isTrue();
    assertThat(regularRaisesAt(behavior, 5)).isTrue();
    assertThat(regularRaisesAt(behavior, 6)).isTrue();
  }

  @Test
  void shouldNotRaiseWhenElementTypeIsDisabled() {
    final var behavior = behavior(3, Map.of(BpmnElementType.SERVICE_TASK, 0), 1);

    assertThat(regularRaisesAt(behavior, 100)).isFalse();
  }

  @Test
  void shouldUsePerTypeOverrideForRegularCheck() {
    final var behavior = behavior(10, Map.of(BpmnElementType.SERVICE_TASK, 2), 1);

    assertThat(regularRaisesAt(behavior, 2)).isFalse();
    assertThat(regularRaisesAt(behavior, 3)).isTrue();
  }

  // ---------------------------------------------------------------------------
  // isChildActivationThresholdExceeded (batch stop condition)
  // ---------------------------------------------------------------------------

  @Test
  void shouldNotStopBatchWithinThreshold() {
    final var behavior = behavior(4, Map.of(), 1);

    // the counter is at the threshold but not beyond it, so the batch keeps spawning children
    assertThat(batchStopsAt(behavior, 4)).isFalse();
  }

  @Test
  void shouldStopBatchBeyondThreshold() {
    final var behavior = behavior(4, Map.of(), 1);

    assertThat(batchStopsAt(behavior, 5)).isTrue();
  }

  @Test
  void shouldResolveBatchStopThresholdAgainstInnerType() {
    // MULTI_INSTANCE_BODY falls back to the global default (10, enabled); SERVICE_TASK caps at 2
    final var behavior = behavior(10, Map.of(BpmnElementType.SERVICE_TASK, 2), 1);

    assertThat(batchStopsAt(behavior, 2)).isFalse();
    assertThat(batchStopsAt(behavior, 3)).isTrue();
  }

  @Test
  void shouldNotStopBatchWhenBodyTypeDisabled() {
    // disabling MULTI_INSTANCE_BODY turns the batch stop off, even when the inner type would breach
    final var behavior =
        behavior(
            10,
            Map.of(
                BpmnElementType.MULTI_INSTANCE_BODY, 0,
                BpmnElementType.SERVICE_TASK, 2),
            1);

    assertThat(batchStopsAt(behavior, 100)).isFalse();
  }

  @Test
  void shouldNotStopBatchWhenInnerTypeDisabled() {
    final var behavior = behavior(10, Map.of(BpmnElementType.SERVICE_TASK, 0), 1);

    assertThat(batchStopsAt(behavior, 100)).isFalse();
  }

  // ---------------------------------------------------------------------------
  // checkMultiInstanceChildActivationThreshold
  // ---------------------------------------------------------------------------

  @Test
  void shouldNotRaiseChildWithinThreshold() {
    final var behavior = behavior(4, Map.of(), 1);

    assertThat(childRaisesAt(behavior, 4)).isFalse();
  }

  @Test
  void shouldRaiseChildBeyondThreshold() {
    final var behavior = behavior(4, Map.of(), 1);

    assertThat(childRaisesAt(behavior, 5)).isTrue();
  }

  @Test
  void shouldThrottleChildReRaisingByCooldown() {
    final var behavior = behavior(4, Map.of(), 3);

    // fires on the first breach (5), then every cooldown (3) activations after that
    assertThat(childRaisesAt(behavior, 5)).isTrue();
    assertThat(childRaisesAt(behavior, 6)).isFalse();
    assertThat(childRaisesAt(behavior, 7)).isFalse();
    assertThat(childRaisesAt(behavior, 8)).isTrue();
  }

  @Test
  void shouldDisableChildCheckWhenBodyTypeDisabled() {
    // MULTI_INSTANCE_BODY = 0 disables loop detection for MI children, even though SERVICE_TASK
    // caps
    // at 2
    final var behavior =
        behavior(
            10,
            Map.of(
                BpmnElementType.MULTI_INSTANCE_BODY, 0,
                BpmnElementType.SERVICE_TASK, 2),
            1);

    assertThat(childRaisesAt(behavior, 100)).isFalse();
  }

  @Test
  void shouldResolveChildThresholdAgainstChildType() {
    // MULTI_INSTANCE_BODY enabled via the global default (10); the child is capped by
    // SERVICE_TASK=2
    final var behavior = behavior(10, Map.of(BpmnElementType.SERVICE_TASK, 2), 1);

    assertThat(childRaisesAt(behavior, 2)).isFalse();
    assertThat(childRaisesAt(behavior, 3)).isTrue();
  }
}
