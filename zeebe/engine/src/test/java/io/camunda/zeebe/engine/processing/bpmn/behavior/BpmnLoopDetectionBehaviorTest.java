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
  private final BpmnElementContext multiInstanceContext = mock(BpmnElementContext.class);

  @BeforeEach
  void setUp() {
    when(serviceTaskContext.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(serviceTaskContext.getElementId()).thenReturn(ELEMENT_ID);
    when(serviceTaskContext.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

    when(multiInstanceContext.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(multiInstanceContext.getElementId()).thenReturn(ELEMENT_ID);
    when(multiInstanceContext.getBpmnElementType()).thenReturn(BpmnElementType.MULTI_INSTANCE_BODY);
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

  private boolean batchRaisesAt(
      final BpmnLoopDetectionBehavior behavior,
      final long childActivationCount,
      final int batchSize) {
    activationCount(childActivationCount);
    return behavior
        .checkBatchActivationThreshold(
            multiInstanceContext, BpmnElementType.SERVICE_TASK, batchSize)
        .isLeft();
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

    // fires on the first breach (4), then only every 3rd activation beyond the threshold
    assertThat(regularRaisesAt(behavior, 4)).isTrue();
    assertThat(regularRaisesAt(behavior, 5)).isFalse();
    assertThat(regularRaisesAt(behavior, 6)).isTrue();
    assertThat(regularRaisesAt(behavior, 7)).isFalse();
    assertThat(regularRaisesAt(behavior, 8)).isFalse();
    assertThat(regularRaisesAt(behavior, 9)).isTrue();
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
  // checkBatchActivationThreshold (coarse pre-spawn guard)
  // ---------------------------------------------------------------------------

  @Test
  void shouldNotRaiseBatchWhenCollectionFitsInFreshBudget() {
    final var behavior = behavior(4, Map.of(), 1);

    // no children activated yet and the whole collection fits within the threshold
    assertThat(batchRaisesAt(behavior, 0, 4)).isFalse();
  }

  @Test
  void shouldRaiseBatchWhenCollectionAloneExceedsThreshold() {
    final var behavior = behavior(4, Map.of(), 1);

    // a single collection larger than the threshold is blocked wholesale on the body
    assertThat(batchRaisesAt(behavior, 0, 5)).isTrue();
  }

  @Test
  void shouldRaiseBatchWhenBudgetAlreadyExhausted() {
    final var behavior = behavior(4, Map.of(), 1);

    // the threshold is already reached by earlier child activations, so no further child fits
    assertThat(batchRaisesAt(behavior, 4, 1)).isTrue();
    assertThat(batchRaisesAt(behavior, 5, 2)).isTrue();
  }

  @Test
  void shouldNotRaiseBatchForPartialOverflow() {
    final var behavior = behavior(4, Map.of(), 1);

    // 2 children already activated + a batch of 3 exceeds the threshold cumulatively, but at least
    // one child still fits and the collection alone is within the limit, so the batch is allowed to
    // spawn; the crossing child is caught by checkMultiInstanceChildActivationThreshold instead
    assertThat(batchRaisesAt(behavior, 2, 3)).isFalse();
  }

  @Test
  void shouldResolveBatchThresholdAgainstInnerType() {
    // MULTI_INSTANCE_BODY falls back to the global default (10, enabled); SERVICE_TASK caps at 2
    final var behavior = behavior(10, Map.of(BpmnElementType.SERVICE_TASK, 2), 1);

    // a collection of 3 exceeds the inner-type threshold of 2 even though the body threshold is 10
    assertThat(batchRaisesAt(behavior, 0, 3)).isTrue();
    // a collection of 2 fits the inner-type threshold of 2
    assertThat(batchRaisesAt(behavior, 0, 2)).isFalse();
  }

  @Test
  void shouldNotRaiseBatchWhenBodyTypeDisabled() {
    // disabling MULTI_INSTANCE_BODY turns the batch guard off, even when the inner type would
    // breach
    final var behavior =
        behavior(
            10,
            Map.of(
                BpmnElementType.MULTI_INSTANCE_BODY, 0,
                BpmnElementType.SERVICE_TASK, 2),
            1);

    assertThat(batchRaisesAt(behavior, 0, 100)).isFalse();
  }

  @Test
  void shouldNotRaiseBatchWhenInnerTypeDisabled() {
    final var behavior = behavior(10, Map.of(BpmnElementType.SERVICE_TASK, 0), 1);

    assertThat(batchRaisesAt(behavior, 0, 100)).isFalse();
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

    // fires on the first breach (5), then only every 3rd child activation beyond the threshold
    assertThat(childRaisesAt(behavior, 5)).isTrue();
    assertThat(childRaisesAt(behavior, 6)).isFalse();
    assertThat(childRaisesAt(behavior, 7)).isTrue();
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
