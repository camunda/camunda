/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.timer;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.record.intent.TimerIntent;
import java.util.function.Consumer;

/**
 * Temporary processor for timer creation. Currently necessary until all processors which may
 * produce Timer.CREATED have been migrated to event-sourcing, otherwise events may be applied
 * twice.
 *
 * <p>TODO(npepinpe): remove as part of https://github.com/camunda-cloud/zeebe/issues/6589
 */
public final class CreateTimerProcessor implements TypedRecordProcessor<TimerRecord> {
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final DueDateTimerChecker dueDateTimerChecker;

  public CreateTimerProcessor(
      final StateWriter stateWriter,
      final KeyGenerator keyGenerator,
      final DueDateTimerChecker dueDateTimerChecker) {
    this.stateWriter = stateWriter;
    this.keyGenerator = keyGenerator;
    this.dueDateTimerChecker = dueDateTimerChecker;
  }

  @Override
  public void processRecord(
      final TypedRecord<TimerRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    final long timerKey = keyGenerator.nextKey();
    final long dueDate = record.getValue().getDueDate();
    sideEffect.accept(
        () -> {
          dueDateTimerChecker.scheduleTimer(dueDate);
          return true;
        });

    stateWriter.appendFollowUpEvent(timerKey, TimerIntent.CREATED, record.getValue());
  }
}
