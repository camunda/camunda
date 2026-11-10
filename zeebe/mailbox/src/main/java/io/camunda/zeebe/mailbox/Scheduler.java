/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Runs a task once after a delay. A test replaces it with a scheduler that it controls. */
@FunctionalInterface
public interface Scheduler {

  void schedule(Runnable task, Duration delay);

  /** Uses an executor, which may be a thread of its own so that blocking calls are allowed. */
  static Scheduler of(final ScheduledExecutorService executor) {
    return (task, delay) -> executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
  }
}
