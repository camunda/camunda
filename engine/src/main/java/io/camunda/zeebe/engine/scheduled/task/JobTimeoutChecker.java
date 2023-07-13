/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.scheduled.task;

import io.camunda.zeebe.engine.scheduled.ScheduledEngineTask;
import io.camunda.zeebe.engine.scheduled.ScheduledEngineTask.SchedulingDecision.Delayed;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.time.Duration;

public final class JobTimeoutChecker implements ScheduledEngineTask {

  public static final Duration INTERVAL = Duration.ofSeconds(30);

  @Override
  public Result execute(final Context context) {
    final var state = context.state().getJobState();
    final var builder = context.resultBuilder();
    final var now = context.clock().millis();

    state.forEachTimedOutEntry(
        now, (key, record) -> builder.appendCommandRecord(key, JobIntent.TIME_OUT, record));

    return new Result(context.resultBuilder().build(), new Delayed(INTERVAL));
  }
}
