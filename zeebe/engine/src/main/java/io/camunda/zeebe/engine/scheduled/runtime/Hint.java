/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

/**
 * A scheduling hint returned by a {@link ScheduledTask}. Hints are advisory: the runtime may honor
 * or override them based on the registered schedule, pause/throttle state, and back-pressure
 * signals.
 */
public sealed interface Hint {

  /** See {@link Result#idle}. */
  record Idle() implements Hint {}

  /** See {@link Result#moreWorkPending}. */
  record MoreWorkPending() implements Hint {}

  /** See {@link Result#nextDueAt}. */
  record NextDueAt(long timestamp) implements Hint {}
}
