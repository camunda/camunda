/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class TimerInstanceStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableTimerInstanceState state;

  @Before
  public void setUp() {
    final MutableProcessingState processingState = stateRule.getProcessingState();
    state = processingState.getTimerState();
  }

  @Test
  public void shouldInsertTimer() {
    // given
    createTimerInstance(1, 2, 1000L);

    // when
    final List<TimerInstance> timers = new ArrayList<>();
    state.processTimersWithDueDateBefore(1000L, timers::add);

    // then
    Assertions.assertThat(timers).hasSize(1);

    final TimerInstance readTimer = timers.get(0);
    Assertions.assertThat(readTimer.getElementInstanceKey()).isEqualTo(1L);
    Assertions.assertThat(readTimer.getKey()).isEqualTo(2L);
    Assertions.assertThat(readTimer.getDueDate()).isEqualTo(1000L);
  }

  @Test
  public void shouldRemoveTimer() {
    // given
    createTimerInstance(1, 0, 1000L);
    createTimerInstance(2, 0, 2000L);

    // when
    final TimerInstance timer = new TimerInstance();
    timer.setElementInstanceKey(1L);
    timer.setDueDate(1000L);
    state.remove(timer);

    // then
    final List<TimerInstance> timers = new ArrayList<>();
    state.processTimersWithDueDateBefore(2000L, timers::add);

    Assertions.assertThat(timers).hasSize(1);
    Assertions.assertThat(timers.get(0).getElementInstanceKey()).isEqualTo(2L);
  }

  @Test
  public void shouldGetTimerByElementInstanceKey() {
    // given
    createElementInstance(1L);
    final TimerInstance timer = new TimerInstance();
    timer.setElementInstanceKey(1L);
    timer.setProcessInstanceKey(1L);
    timer.setKey(2L);
    timer.setDueDate(1000L);
    state.store(timer);

    // when
    final TimerInstance readTimer = state.get(1L, 2L);

    // then
    Assertions.assertThat(readTimer).isNotNull();
    Assertions.assertThat(readTimer.getElementInstanceKey()).isEqualTo(1L);
    Assertions.assertThat(readTimer.getKey()).isEqualTo(2L);
    Assertions.assertThat(readTimer.getProcessInstanceKey()).isEqualTo(1L);
    Assertions.assertThat(readTimer.getDueDate()).isEqualTo(1000L);

    // and
    Assertions.assertThat(state.get(2L, 1L)).isNull();
  }

  @Test
  public void shouldFindTimersWithDueDate() {
    // given
    createTimerInstance(1, 1, 1000L);
    createTimerInstance(2, 2, 2000L);
    createTimerInstance(3, 3, 3000L);

    // when
    final List<Long> keys = new ArrayList<>();
    state.processTimersWithDueDateBefore(2000L, t -> keys.add(t.getElementInstanceKey()));

    // then
    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  @Test
  public void shouldReturnNextDueDate() {
    // given
    createTimerInstance(1, 1, 1000L);
    createTimerInstance(2, 2, 2000L);
    createTimerInstance(3, 3, 3000L);

    // when
    final long nextDueDate = state.processTimersWithDueDateBefore(2000L, t -> true);

    // then
    assertThat(nextDueDate).isEqualTo(3000L);
  }

  @Test
  public void shouldReturnNegativeDueDateIfEmpty() {

    // when
    final long nextDueDate = state.processTimersWithDueDateBefore(2000L, t -> true);

    // then
    assertThat(nextDueDate).isEqualTo(-1L);
  }

  @Test
  public void shouldReturnNegativeDueDateIfNoMoreTimers() {
    // given
    createTimerInstance(1, 1, 1000L);
    createTimerInstance(2, 2, 2000L);
    createTimerInstance(3, 3, 3000L);

    // when
    final long nextDueDate = state.processTimersWithDueDateBefore(3000L, t -> true);

    // then
    assertThat(nextDueDate).isEqualTo(-1L);
  }

  @Test
  public void shouldFindTimersWithDueDateUntilNotConsumed() {
    // given
    final var timer1 = createTimerInstance(1, 1, 1000L);
    createTimerInstance(2, 2, 2000L);

    // when
    final List<Long> keys = new ArrayList<>();
    final long nextDueDate =
        state.processTimersWithDueDateBefore(
            2000L,
            t -> {
              keys.add(t.getElementInstanceKey());
              return false;
            });

    // then
    assertThat(keys).containsExactly(1L);
    assertThat(nextDueDate).isEqualTo(timer1.getDueDate());
  }

  @Test
  public void shouldListAllTimersByElementInstanceKey() {
    // given
    createElementInstance(1);
    createElementInstance(2);
    final TimerInstance timer1 = new TimerInstance();
    timer1.setElementInstanceKey(1L);
    timer1.setKey(1L);
    timer1.setDueDate(1000L);
    state.store(timer1);

    final TimerInstance timer2 = new TimerInstance();
    timer2.setElementInstanceKey(1L);
    timer2.setKey(2L);
    timer2.setDueDate(2000L);
    state.store(timer2);

    final TimerInstance timer3 = new TimerInstance();
    timer3.setElementInstanceKey(2L);
    timer3.setKey(3L);
    timer3.setDueDate(2000L);
    state.store(timer3);

    // when
    final List<Long> keys = new ArrayList<>();
    state.forEachTimerForElementInstance(1L, t -> keys.add(t.getKey()));

    // then
    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  private TimerInstance createTimerInstance(
      final long elementInstanceKey, final long timerKey, final long dueDate) {
    createElementInstance(elementInstanceKey);
    final TimerInstance timer = new TimerInstance();
    timer.setElementInstanceKey(elementInstanceKey);
    timer.setKey(timerKey);
    timer.setDueDate(dueDate);
    state.store(timer);
    return timer;
  }

  private void createElementInstance(final long key) {
    stateRule
        .getProcessingState()
        .getElementInstanceState()
        .createInstance(
            new ElementInstance(
                key, ProcessInstanceIntent.ELEMENT_ACTIVATED, new ProcessInstanceRecord()));
  }
}
