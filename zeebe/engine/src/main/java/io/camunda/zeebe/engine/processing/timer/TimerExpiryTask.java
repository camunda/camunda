/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import io.camunda.zeebe.engine.scheduled.runtime.Context;
import io.camunda.zeebe.engine.scheduled.runtime.Result;
import io.camunda.zeebe.engine.scheduled.runtime.ScheduledTask;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState.TimerVisitor;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;

/**
 * Triggers due timers and returns a scheduling hint based on what {@link
 * TimerInstanceState#processTimersWithDueDateBefore} reports. Owns the same yielding logic that
 * previously lived in {@code TriggerTimersSideEffect}, {@code YieldingDecorator}, and {@code
 * WriteTriggerTimerCommandVisitor}.
 */
public final class TimerExpiryTask implements ScheduledTask {

  static final long TIMER_RESOLUTION_MS = Duration.ofMillis(100).toMillis();
  private static final double GIVE_YIELD_FACTOR = 0.5;

  private final TimerInstanceState state;
  private final InstantSource clock;
  private final boolean yieldEnabled;

  public TimerExpiryTask(
      final TimerInstanceState state, final InstantSource clock, final boolean yieldEnabled) {
    this.state = state;
    this.clock = clock;
    this.yieldEnabled = yieldEnabled;
  }

  @Override
  public Result run(final Context context) {
    final var builder = context.resultBuilder();
    final long now = clock.millis();
    final long yieldAfter = now + Math.round(TIMER_RESOLUTION_MS * GIVE_YIELD_FACTOR);

    final var emitter = new WriteTrigger(builder);
    final TimerVisitor visitor =
        yieldEnabled ? new YieldingDecorator(clock, yieldAfter, emitter) : emitter;

    final long nextDueDate = state.processTimersWithDueDateBefore(now, visitor);

    if (emitter.yieldedDueToBatchFull || (yieldEnabled && emitter.yieldedDueToTime)) {
      return Result.moreWorkPending(builder);
    }
    if (nextDueDate > 0) {
      return Result.nextDueAt(nextDueDate, builder);
    }
    return Result.idle(builder);
  }

  private static final class WriteTrigger implements TimerVisitor {

    boolean yieldedDueToBatchFull;
    boolean yieldedDueToTime;
    private final TaskResultBuilder builder;
    private final TimerRecord record = new TimerRecord();

    WriteTrigger(final TaskResultBuilder builder) {
      this.builder = builder;
    }

    @Override
    public boolean visit(final TimerInstance timer) {
      record.reset();
      record
          .setElementInstanceKey(timer.getElementInstanceKey())
          .setProcessInstanceKey(timer.getProcessInstanceKey())
          .setDueDate(timer.getDueDate())
          .setTargetElementId(timer.getHandlerNodeId())
          .setRepetitions(timer.getRepetitions())
          .setProcessDefinitionKey(timer.getProcessDefinitionKey())
          .setTenantId(timer.getTenantId());
      final boolean appended =
          builder.appendCommandRecord(timer.getKey(), TimerIntent.TRIGGER, record);
      if (!appended) {
        yieldedDueToBatchFull = true;
      }
      return appended;
    }
  }

  private static final class YieldingDecorator implements TimerVisitor {

    private final InstantSource clock;
    private final long yieldAfter;
    private final WriteTrigger delegate;

    YieldingDecorator(
        final InstantSource clock, final long yieldAfter, final WriteTrigger delegate) {
      this.clock = clock;
      this.yieldAfter = yieldAfter;
      this.delegate = delegate;
    }

    @Override
    public boolean visit(final TimerInstance timer) {
      if (clock.millis() >= yieldAfter) {
        delegate.yieldedDueToTime = true;
        return false;
      }
      return delegate.visit(timer);
    }
  }
}
