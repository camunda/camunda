/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class TimerSuspendedApplierTest {

  private static final long ELEMENT_INSTANCE_KEY = 1L;
  private static final long TIMER_KEY = 2L;
  private static final long DUE_DATE = 1000L;

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableTimerInstanceState timerState;
  private TimerSuspendedApplier suspendedApplier;
  private TimerTriggeredApplier triggeredApplier;
  private TimerCancelledApplier cancelledApplier;
  private TimerResumedApplier resumedApplier;

  @BeforeEach
  void setUp() {
    timerState = processingState.getTimerState();
    suspendedApplier = new TimerSuspendedApplier(timerState);
    triggeredApplier = new TimerTriggeredApplier(timerState);
    cancelledApplier = new TimerCancelledApplier(timerState);
    resumedApplier = new TimerResumedApplier(timerState);
  }

  @Test
  void shouldDropDueDateAndKeepTimer() {
    // given
    storeTimer();

    // when
    suspendedApplier.applyState(TIMER_KEY, timerRecord());

    // then
    assertThat(timerState.get(ELEMENT_INSTANCE_KEY, TIMER_KEY)).isNotNull();
    assertThat(timerState.hasDueDateEntry(ELEMENT_INSTANCE_KEY, TIMER_KEY)).isFalse();
    assertThat(dueTimers()).isEmpty();
  }

  @Test
  void shouldRemoveTimerOnTriggeredAfterSuspend() {
    // given
    storeTimer();
    suspendedApplier.applyState(TIMER_KEY, timerRecord());

    // when
    triggeredApplier.applyState(TIMER_KEY, timerRecord());

    // then
    assertThat(timerState.get(ELEMENT_INSTANCE_KEY, TIMER_KEY)).isNull();
  }

  @Test
  void shouldRemoveTimerOnCanceledAfterSuspend() {
    // given
    storeTimer();
    suspendedApplier.applyState(TIMER_KEY, timerRecord());

    // when
    cancelledApplier.applyState(TIMER_KEY, timerRecord());

    // then
    assertThat(timerState.get(ELEMENT_INSTANCE_KEY, TIMER_KEY)).isNull();
  }

  @Test
  void shouldRestoreDueDateOnResume() {
    // given
    storeTimer();
    suspendedApplier.applyState(TIMER_KEY, timerRecord());

    // when
    resumedApplier.applyState(TIMER_KEY, timerRecord());

    // then
    assertThat(timerState.get(ELEMENT_INSTANCE_KEY, TIMER_KEY)).isNotNull();
    assertThat(timerState.hasDueDateEntry(ELEMENT_INSTANCE_KEY, TIMER_KEY)).isTrue();
    assertThat(dueTimers()).extracting(TimerInstance::getKey).containsExactly(TIMER_KEY);
  }

  @Test
  void shouldKeepExistingDueDateOnResume() {
    // given
    storeTimer();

    // when
    resumedApplier.applyState(TIMER_KEY, timerRecord());

    // then
    assertThat(timerState.hasDueDateEntry(ELEMENT_INSTANCE_KEY, TIMER_KEY)).isTrue();
    assertThat(dueTimers()).extracting(TimerInstance::getKey).containsExactly(TIMER_KEY);
  }

  @Test
  void shouldNotRestoreDueDateWhenTimerNoLongerExists() {
    // given
    storeTimer();
    suspendedApplier.applyState(TIMER_KEY, timerRecord());
    triggeredApplier.applyState(TIMER_KEY, timerRecord());

    // when
    resumedApplier.applyState(TIMER_KEY, timerRecord());

    // then
    assertThat(timerState.hasDueDateEntry(ELEMENT_INSTANCE_KEY, TIMER_KEY)).isFalse();
    assertThat(dueTimers()).isEmpty();
  }

  private void storeTimer() {
    processingState
        .getElementInstanceState()
        .createInstance(
            new ElementInstance(
                ELEMENT_INSTANCE_KEY,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                new ProcessInstanceRecord()));
    final TimerInstance timer = new TimerInstance();
    timer.setElementInstanceKey(ELEMENT_INSTANCE_KEY);
    timer.setKey(TIMER_KEY);
    timer.setDueDate(DUE_DATE);
    timerState.store(timer);
  }

  private TimerRecord timerRecord() {
    return new TimerRecord()
        .setElementInstanceKey(ELEMENT_INSTANCE_KEY)
        .setDueDate(DUE_DATE)
        .setTargetElementId(BufferUtil.wrapString("timer"))
        .setRepetitions(0)
        .setProcessDefinitionKey(1)
        .setProcessInstanceKey(ELEMENT_INSTANCE_KEY);
  }

  private List<TimerInstance> dueTimers() {
    final List<TimerInstance> timers = new ArrayList<>();
    timerState.processTimersWithDueDateBefore(DUE_DATE, timers::add);
    return timers;
  }
}
