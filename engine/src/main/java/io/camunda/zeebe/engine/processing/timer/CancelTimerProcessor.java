/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.timer;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.RejectionsBuilder;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateBuilder;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;

public final class CancelTimerProcessor implements TypedRecordProcessor<TimerRecord> {
  public static final String NO_TIMER_FOUND_MESSAGE =
      "Expected to cancel timer with key '%d', but no such timer was found";

  private final TimerInstanceState timerInstanceState;
  private final StateBuilder stateBuilder;
  private final RejectionsBuilder rejectionWriter;

  public CancelTimerProcessor(
      final TimerInstanceState timerInstanceState,
      final StateBuilder stateBuilder,
      final RejectionsBuilder rejectionWriter) {
    this.timerInstanceState = timerInstanceState;
    this.stateBuilder = stateBuilder;
    this.rejectionWriter = rejectionWriter;
  }

  @Override
  public void processRecord(final TypedRecord<TimerRecord> record) {
    final TimerRecord timer = record.getValue();
    final TimerInstance timerInstance =
        timerInstanceState.get(timer.getElementInstanceKey(), record.getKey());

    if (timerInstance == null) {
      rejectionWriter.appendRejection(
          record, RejectionType.NOT_FOUND, String.format(NO_TIMER_FOUND_MESSAGE, record.getKey()));
    } else {
      stateBuilder.appendFollowUpEvent(record.getKey(), TimerIntent.CANCELED, timer);
    }
  }
}
