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
import org.agrona.DirectBuffer;

/**
 * Detects unintended tight loops in a BPMN process by counting how many times each element is
 * activated within a single process instance. When the count exceeds the configured threshold a
 * {@link Failure} with {@link ErrorType#CONDITION_ERROR} is returned, raising an incident that
 * halts the loop.
 *
 * <p>The threshold can be overridden per {@link BpmnElementType}; a value of {@code 0} disables
 * detection for that type. Multi-instance children accumulate the count on the shared element-id
 * counter and are checked individually via {@link #checkMultiInstanceChildActivationThreshold}. The
 * activation batch of a parallel multi-instance body stops spawning children once {@link
 * #isChildActivationThresholdExceeded} reports the limit is crossed, so a runaway collection is
 * bounded to a single incident on the crossing child.
 */
public final class BpmnLoopDetectionBehavior {

  private static final String CONDITION_ERROR_ACTIVATION_COUNT_EXCEEDED =
      "Expected to activate element '%s' in process instance '%d', but the element has already"
          + " been activated %d times, exceeding the maximum activation threshold of %d.";

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
    // The incident always fires on the first activation beyond the threshold and is then throttled
    // to every retryCooldown activations after that (see shouldRaiseIncident).
    final long activationsBeyondThreshold = activationCount - max;
    if (!shouldRaiseIncident(activationsBeyondThreshold)) {
      return Either.right(null);
    }

    final String elementId = BufferUtil.bufferAsString(context.getElementId());
    final String message =
        CONDITION_ERROR_ACTIVATION_COUNT_EXCEEDED.formatted(
            elementId, context.getProcessInstanceKey(), activationCount, max);
    return Either.left(new Failure(message, ErrorType.CONDITION_ERROR));
  }

  /**
   * Runs the activation-threshold check for a child of a multi-instance body. Multi-instance
   * children participate in loop detection only when detection is enabled for the {@code
   * MULTI_INSTANCE_BODY} type, so disabling the body type also disables its children. The child is
   * the inner activity, so the threshold and the retry-cooldown throttling are resolved against the
   * child's own element type by delegating to {@link #checkActivationThreshold}.
   *
   * @param childContext the context of the multi-instance child being activated
   * @return {@code Either.right(null)} when within bounds, or {@code Either.left} with a {@link
   *     Failure} when the threshold is exceeded
   */
  public Either<Failure, Void> checkMultiInstanceChildActivationThreshold(
      final BpmnElementContext childContext) {
    if (resolveMaxActivations(BpmnElementType.MULTI_INSTANCE_BODY) <= 0) {
      // Loop detection is disabled for the multi-instance body, so its children are not checked.
      return Either.right(null);
    }
    return checkActivationThreshold(childContext);
  }

  /**
   * Returns whether the multi-instance child-activation counter has already crossed the threshold,
   * so the batch that spawns the children can stop instead of activating (and raising an incident
   * for) every remaining child. Mirrors the gating of {@link
   * #checkMultiInstanceChildActivationThreshold}: detection must be enabled for the {@code
   * MULTI_INSTANCE_BODY} type and the resolved limit for the inner element type must be positive.
   *
   * @param processInstanceKey the process instance the multi-instance body belongs to
   * @param elementId the shared element id of the multi-instance body and its children
   * @param innerElementType the {@link BpmnElementType} of the inner (child) activity
   * @return {@code true} if the counter is strictly above the resolved threshold
   */
  public boolean isChildActivationThresholdExceeded(
      final long processInstanceKey,
      final DirectBuffer elementId,
      final BpmnElementType innerElementType) {
    if (resolveMaxActivations(BpmnElementType.MULTI_INSTANCE_BODY) <= 0) {
      return false;
    }
    final int max = resolveMaxActivations(innerElementType);
    if (max <= 0) {
      return false;
    }
    return elementInstanceState.getElementActivationCounter(processInstanceKey, elementId) > max;
  }

  /**
   * Decides whether an incident should be raised for this breach of the threshold. The incident is
   * always raised on the first breach so it is never skipped; after that, re-raising is throttled
   * to once every {@code retryCooldown} breaches. A {@code retryCooldown} of {@code 1} or less
   * disables throttling (every breach raises an incident) and avoids a divide-by-zero in the modulo
   * below.
   *
   * @param breachCount how many activations have occurred at or beyond the breach point, counting
   *     from {@code 1} for the first activation that breaches the threshold
   */
  private boolean shouldRaiseIncident(final long breachCount) {
    return retryCooldown <= 1 || (breachCount - 1) % retryCooldown == 0;
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
