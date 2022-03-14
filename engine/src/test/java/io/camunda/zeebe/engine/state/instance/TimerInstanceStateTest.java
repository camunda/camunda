/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.engine.util.ZeebeStateRule;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class TimerInstanceStateTest {

  @Rule public final ZeebeStateRule stateRule = new ZeebeStateRule();

  private MutableTimerInstanceState state;

  @Before
  public void setUp() {
    final MutableZeebeState zeebeState = stateRule.getZeebeState();
    state = zeebeState.getTimerState();
  }

  @Test
  public void shouldInsertTimer() {
    // given
    final TimerInstance timer = new TimerInstance();
    timer.setElementInstanceKey(1L);
    timer.setKey(2L);
    timer.setDueDate(1000L);
    state.store(timer);

    // when
    final List<TimerInstance> timers = new ArrayList<>();
    state.findTimersWithDueDateBefore(1000L, timers::add);

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
    final TimerInstance timer1 = new TimerInstance();
    timer1.setElementInstanceKey(1L);
    timer1.setDueDate(1000L);
    state.store(timer1);

    final TimerInstance timer2 = new TimerInstance();
    timer2.setElementInstanceKey(2L);
    timer2.setDueDate(2000L);
    state.store(timer2);

    // when
    final TimerInstance timer = new TimerInstance();
    timer.setElementInstanceKey(1L);
    timer.setDueDate(1000L);
    state.remove(timer);

    // then
    final List<TimerInstance> timers = new ArrayList<>();
    state.findTimersWithDueDateBefore(2000L, timers::add);

    Assertions.assertThat(timers).hasSize(1);
    Assertions.assertThat(timers.get(0).getElementInstanceKey()).isEqualTo(2L);
  }

  @Test
  public void shouldGetTimerByElementInstanceKey() {
    // given
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
    final TimerInstance timer1 = new TimerInstance();
    timer1.setElementInstanceKey(1L);
    timer1.setDueDate(1000L);
    state.store(timer1);

    final TimerInstance timer2 = new TimerInstance();
    timer2.setElementInstanceKey(2L);
    timer2.setDueDate(2000L);
    state.store(timer2);

    final TimerInstance timer3 = new TimerInstance();
    timer3.setElementInstanceKey(3L);
    timer3.setDueDate(3000L);
    state.store(timer3);

    // when
    final List<Long> keys = new ArrayList<>();
    state.findTimersWithDueDateBefore(2000L, t -> keys.add(t.getElementInstanceKey()));

    // then
    assertThat(keys).hasSize(2);
    assertThat(keys).containsExactly(1L, 2L);
  }

  @Test
  public void shouldReturnNextDueDate() {
    // given
    final TimerInstance timer1 = new TimerInstance();
    timer1.setElementInstanceKey(1L);
    timer1.setDueDate(1000L);
    state.store(timer1);

    final TimerInstance timer2 = new TimerInstance();
    timer2.setElementInstanceKey(2L);
    timer2.setDueDate(2000L);
    state.store(timer2);

    final TimerInstance timer3 = new TimerInstance();
    timer3.setElementInstanceKey(3L);
    timer3.setDueDate(3000L);
    state.store(timer3);

    // when
    final long nextDueDate = state.findTimersWithDueDateBefore(2000L, t -> true);

    // then
    assertThat(nextDueDate).isEqualTo(3000L);
  }

  @Test
  public void shouldReturnNegativeDueDateIfEmpty() {

    // when
    final long nextDueDate = state.findTimersWithDueDateBefore(2000L, t -> true);

    // then
    assertThat(nextDueDate).isEqualTo(-1L);
  }

  @Test
  public void shouldReturnNegativeDueDateIfNoMoreTimers() {
    // given
    final TimerInstance timer1 = new TimerInstance();
    timer1.setElementInstanceKey(1L);
    timer1.setDueDate(1000L);
    state.store(timer1);

    final TimerInstance timer2 = new TimerInstance();
    timer2.setElementInstanceKey(2L);
    timer2.setDueDate(1000L);
    state.store(timer2);

    final TimerInstance timer3 = new TimerInstance();
    timer3.setElementInstanceKey(3L);
    timer3.setDueDate(3000L);
    state.store(timer3);

    // when
    final long nextDueDate = state.findTimersWithDueDateBefore(3000L, t -> true);

    // then
    assertThat(nextDueDate).isEqualTo(-1L);
  }

  @Test
  public void shouldFindTimersWithDueDateUntilNotConsumed() {
    // given
    final TimerInstance timer1 = new TimerInstance();
    timer1.setElementInstanceKey(1L);
    timer1.setDueDate(1000L);
    state.store(timer1);

    final TimerInstance timer2 = new TimerInstance();
    timer2.setElementInstanceKey(2L);
    timer2.setDueDate(2000L);
    state.store(timer2);

    // when
    final List<Long> keys = new ArrayList<>();
    final long nextDueDate =
        state.findTimersWithDueDateBefore(
            2000L,
            t -> {
              keys.add(t.getElementInstanceKey());
              return false;
            });

    // then
    assertThat(keys).hasSize(1);
    assertThat(keys).contains(1L);
    assertThat(nextDueDate).isEqualTo(timer1.getDueDate());
  }

  @Test
  public void shouldListAllTimersByElementInstanceKey() {
    // given
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
    assertThat(keys).hasSize(2);
    assertThat(keys).containsExactly(1L, 2L);
  }
}
