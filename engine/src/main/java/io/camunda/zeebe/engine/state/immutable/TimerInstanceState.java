/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.immutable;

import io.zeebe.engine.state.instance.TimerInstance;
import java.util.function.Consumer;

public interface TimerInstanceState {

  long findTimersWithDueDateBefore(long timestamp, TimerVisitor consumer);

  /**
   * NOTE: the timer instance given to the consumer is shared and will be mutated on the next
   * iteration.
   */
  void forEachTimerForElementInstance(long elementInstanceKey, Consumer<TimerInstance> action);

  TimerInstance get(long elementInstanceKey, long timerKey);

  @FunctionalInterface
  interface TimerVisitor {
    boolean visit(TimerInstance timer);
  }
}
