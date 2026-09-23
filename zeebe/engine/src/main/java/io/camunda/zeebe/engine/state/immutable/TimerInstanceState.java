/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.instance.TimerInstance;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public interface TimerInstanceState {

  /**
   * Finds timers with due date before {@code timestamp}, and presents them to the {@code consumer}
   *
   * @return due date of the next scheduled timer (or {@code -1} if no succeeding timer exists)
   */
  long processTimersWithDueDateBefore(long timestamp, TimerVisitor consumer);

  /**
   * NOTE: the timer instance given to the consumer is shared and will be mutated on the next
   * iteration.
   */
  void forEachTimerForElementInstance(long elementInstanceKey, Consumer<TimerInstance> action);

  TimerInstance get(long elementInstanceKey, long timerKey);

  /**
   * Resolves a held timer by its owning process instance and the BPMN element id of its catch
   * event. Only held timers are considered, so scheduled (non-held) timers and unknown elements
   * resolve to {@link HeldTimerResolution.Status#NOT_FOUND}. When more than one held timer matches
   * (e.g. a multi-instance element), the result is {@link HeldTimerResolution.Status#AMBIGUOUS}.
   * Used by the public trigger-timer command, which addresses a held timer by (processInstanceKey,
   * elementId).
   */
  HeldTimerResolution resolveHeldByProcessElement(long processInstanceKey, DirectBuffer elementId);

  /**
   * Outcome of {@link #resolveHeldByProcessElement}. On {@link Status#FOUND} the {@code timer} is
   * the single matching held timer; otherwise it is {@code null}.
   *
   * <p>NOTE: the returned timer is shared and will be mutated on the next state access.
   */
  record HeldTimerResolution(Status status, TimerInstance timer) {

    public enum Status {
      FOUND,
      NOT_FOUND,
      AMBIGUOUS
    }

    public static HeldTimerResolution found(final TimerInstance timer) {
      return new HeldTimerResolution(Status.FOUND, timer);
    }

    public static HeldTimerResolution notFound() {
      return new HeldTimerResolution(Status.NOT_FOUND, null);
    }

    public static HeldTimerResolution ambiguous() {
      return new HeldTimerResolution(Status.AMBIGUOUS, null);
    }
  }

  @FunctionalInterface
  interface TimerVisitor {

    /**
     * @return {@code true} if the timer was processed, or {@code false} if the timer could not be
     *     processed and needs to be revisited later on
     */
    boolean visit(TimerInstance timer);
  }
}
