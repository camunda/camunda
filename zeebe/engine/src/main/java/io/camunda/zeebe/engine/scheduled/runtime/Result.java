/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;

/** Result of one {@link ScheduledTask} run: the produced records plus a {@link Hint}. */
public record Result(TaskResult taskResult, Hint hint) {

  /**
   * The task has nothing more to do right now. The runtime falls back to the registered {@link
   * Schedule}: a {@code Periodic} task runs again after its interval; an {@code OnDemand} task does
   * not run until a nudge arrives.
   */
  public static Result idle(final TaskResultBuilder builder) {
    return new Result(builder.build(), new Hint.Idle());
  }

  /**
   * The task cut its work short (typically because it yielded the actor) and there is more work it
   * could not finish in this slice. The runtime re-arms the task at {@code now + minResolution},
   * subject to flow control and throttling.
   */
  public static Result moreWorkPending(final TaskResultBuilder builder) {
    return new Result(builder.build(), new Hint.MoreWorkPending());
  }

  /**
   * The task has no more work right now but knows the next due moment is at or before {@code
   * timestamp}. Acts as a self-nudge: the runtime pulls the next-run candidate forward to {@code
   * min(candidate, timestamp)}.
   */
  public static Result nextDueAt(final long timestamp, final TaskResultBuilder builder) {
    return new Result(builder.build(), new Hint.NextDueAt(timestamp));
  }
}
