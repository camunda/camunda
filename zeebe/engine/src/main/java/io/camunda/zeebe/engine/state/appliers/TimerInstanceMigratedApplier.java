/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.mutable.MutableTimerInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;

public class TimerInstanceMigratedApplier implements TypedEventApplier<TimerIntent, TimerRecord> {

  private final MutableTimerInstanceState timerInstanceState;

  public TimerInstanceMigratedApplier(final MutableTimerInstanceState timerInstanceState) {
    this.timerInstanceState = timerInstanceState;
  }

  @Override
  public void applyState(final long key, final TimerRecord value) {
    final TimerInstance timerInstance = timerInstanceState.get(value.getElementInstanceKey(), key);
    // only these fields are updated during migration
    timerInstance.setHandlerNodeId(value.getTargetElementIdBuffer());
    timerInstance.setProcessDefinitionKey(value.getProcessDefinitionKey());

    timerInstanceState.update(timerInstance);
  }
}
