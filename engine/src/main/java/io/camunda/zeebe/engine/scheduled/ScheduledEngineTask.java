/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.scheduled;

import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;

/**
 * A scheduled engine task can read from the state, write new commands and reschedule itself to run
 * again.
 *
 * <p>Tasks should be stateless and only use what is being provided by the {@link Context}.
 */
public interface ScheduledEngineTask {

  Result execute(Context context);

  /**
   * Contains the entire context for a single task execution.
   *
   * @param clock A fixed {@link InstantSource} to read the current time. This is not a "live"
   *     clock, it doesn't update while a task is executing.
   * @param state The {@link ScheduledTaskState} that can be used to read from the state.
   * @param resultBuilder A builder that can be used to write new commands.
   */
  record Context(InstantSource clock, ScheduledTaskState state, TaskResultBuilder resultBuilder) {}

  /**
   * Represents the result of a task execution along with the corresponding scheduling decision.
   *
   * @param taskResult Built by calling {@link TaskResultBuilder#build()} on {@link
   *     Context#resultBuilder()}
   */
  record Result(TaskResult taskResult, SchedulingDecision schedulingDecision) {}

  /**
   * A SchedulingDecision is returned as part of the {@link Result}. Currently, the only supported
   * type is {@link Delayed} but this could be extended to support descheduling and wakeup on
   * signal.
   */
  sealed interface SchedulingDecision {

    /** Requests another execution after the delay has passed. */
    record Delayed(Duration delay) implements SchedulingDecision {}
  }
}
