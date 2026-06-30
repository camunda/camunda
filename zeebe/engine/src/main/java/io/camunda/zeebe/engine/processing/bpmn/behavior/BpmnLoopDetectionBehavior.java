/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;

/**
 * Detects unintended tight loops in a BPMN process by counting how many times each element is
 * activated within a single process instance. When the count exceeds the configured threshold a
 * {@link Failure} with {@link ErrorType#CONDITION_ERROR} is returned, raising an incident that
 * halts the loop.
 *
 * <p>The threshold can be overridden per {@link BpmnElementType}; a value of {@code 0} disables
 * detection for that type. Multi-instance elements are handled by the callers ({@code
 * BpmnStreamProcessor} and {@code MultiInstanceBodyProcessor}), which decide whether to count the
 * body or its children to avoid false positives on large collections.
 */
public final class BpmnLoopDetectionBehavior {

  private static final String CONDITION_ERROR_ACTIVATION_COUNT_EXCEEDED =
      "Expected to activate element '%s' in process instance '%d', but the element has already"
          + " been activated %d times, exceeding the maximum activation threshold of %d.";

  private static final String CONDITION_ERROR_BATCH_ACTIVATION_COUNT_EXCEEDED =
      "Expected to activate element '%s' in process instance '%d', but the parallel"
          + " multi-instance body has been activated %d time(s) with a collection of %d item(s),"
          + " resulting in a projected total of %d child activations which exceeds the maximum"
          + " activation threshold of %d.";

  private final MutableElementInstanceState elementInstanceState;
  private final int maxActivations;
  private final Map<BpmnElementType, Integer> maxActivationsByType;
  private final int retryCooldown;

  public BpmnLoopDetectionBehavior(
      final MutableElementInstanceState elementInstanceState,
      final int maxActivations,
      final Map<BpmnElementType, Integer> maxActivationsByType,
      final int retryCooldown) {
    this.elementInstanceState = elementInstanceState;
    this.maxActivations = maxActivations;
    this.maxActivationsByType = Map.copyOf(maxActivationsByType);
    this.retryCooldown = retryCooldown;
  }

  /**
   * Checks whether the activation count for the element has exceeded the configured threshold. The
   * counter is incremented by {@code ProcessInstanceElementActivatingV4Applier} before this check
   * runs, so it already reflects the current activation. A {@link Failure} is raised on the first
   * activation beyond the threshold and then every {@code retryCooldown} activations after that.
   *
   * @param context the context of the element that is being activated
   * @return {@code Either.right(null)} when within bounds, or {@code Either.left} with a {@link
   *     Failure} when the threshold is exceeded
   */
  public Either<Failure, Void> checkActivationThreshold(final BpmnElementContext context) {
    final int max = resolveMaxActivations(context.getBpmnElementType());
    if (max <= 0) {
      // Loop detection is disabled for this element type.
      return Either.right(null);
    }

    // The counter was already incremented by ProcessInstanceElementActivatingV4Applier when the
    // ELEMENT_ACTIVATING event was applied (during transitionToActivating, before this check runs),
    // so it already reflects the current activation.
    final long activationCount =
        elementInstanceState.getElementActivationCounter(
            context.getProcessInstanceKey(), context.getElementId());

    if (activationCount <= max) {
      return Either.right(null);
    }
    // Always fire on the first activation beyond the threshold so the incident is never skipped;
    // after that, throttle re-raising to every retryCooldown activations beyond the threshold. A
    // retryCooldown of 0 (or less) disables throttling so the incident is re-raised on every
    // activation beyond the threshold; the guard also avoids a divide-by-zero in the modulo below.
    final long activationsBeyondThreshold = activationCount - max;
    if (retryCooldown > 1
        && activationsBeyondThreshold > 1
        && activationsBeyondThreshold % retryCooldown != 0) {
      return Either.right(null);
    }

    final String elementId = BufferUtil.bufferAsString(context.getElementId());
    final String message =
        CONDITION_ERROR_ACTIVATION_COUNT_EXCEEDED.formatted(
            elementId, context.getProcessInstanceKey(), activationCount, max);
    return Either.left(new Failure(message, ErrorType.CONDITION_ERROR));
  }

  /**
   * Pre-spawn check for <em>parallel</em> multi-instance bodies, called before the child batch is
   * activated so a large-collection loop can be stopped early. Fires when {@code bodyCount ×
   * batchSize > maxActivations}, where {@code bodyCount} is the body activation count already
   * incremented for this iteration.
   *
   * @param context the context of the {@code MULTI_INSTANCE_BODY} element being activated
   * @param batchSize the number of child instances that would be spawned in the next batch
   * @return {@code Either.right(null)} when within bounds, or {@code Either.left} with a {@link
   *     Failure} when the threshold is exceeded
   */
  public Either<Failure, Void> checkBatchActivationThreshold(
      final BpmnElementContext context, final int batchSize) {
    final int max = resolveMaxActivations(context.getBpmnElementType());
    if (max <= 0) {
      // Loop detection is disabled for this element type.
      return Either.right(null);
    }
    final long bodyCount =
        elementInstanceState.getElementActivationCounter(
            context.getProcessInstanceKey(), context.getElementId());

    final long projectedTotal = bodyCount * batchSize;
    if (projectedTotal <= max) {
      return Either.right(null);
    }

    final String elementId = BufferUtil.bufferAsString(context.getElementId());
    final String message =
        CONDITION_ERROR_BATCH_ACTIVATION_COUNT_EXCEEDED.formatted(
            elementId, context.getProcessInstanceKey(), bodyCount, batchSize, projectedTotal, max);
    return Either.left(new Failure(message, ErrorType.CONDITION_ERROR));
  }

  /**
   * Resolves the effective maximum activation count for the given element type. Returns the
   * per-type override when configured, otherwise the global default. A value of {@code 0} means
   * loop detection is disabled for that type.
   */
  private int resolveMaxActivations(final BpmnElementType elementType) {
    final Integer override = maxActivationsByType.get(elementType);
    return override != null ? override : maxActivations;
  }
}
