/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.instance.TimerInstance;
import java.util.function.Consumer;

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

  @FunctionalInterface
  interface TimerVisitor {

    /**
     * @return {@code true} if the timer was processed, or {@code false} if the timer could not be
     *     processed and needs to be revisited later on
     */
    boolean visit(TimerInstance timer);
  }
}
