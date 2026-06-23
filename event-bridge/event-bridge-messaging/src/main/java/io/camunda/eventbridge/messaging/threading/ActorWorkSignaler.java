/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.threading;

import io.camunda.zeebe.scheduler.ActorControl;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A lock-free utility that prevents Actor mailbox flooding by conflating rapid concurrent signals.
 *
 * <p><b>Contract:</b> If multiple threads signal this utility concurrently, it guarantees that
 * exactly ONE job is submitted to the Actor's mailbox.
 *
 * <p><b>Zero Stranding:</b> The internal flag is reset immediately before the task runs on the
 * Actor thread. This opens the window so any concurrent signals that arrive while the task is
 * actively executing will successfully schedule a subsequent execution.
 */
public final class ActorWorkSignaler {

  private final AtomicBoolean isScheduled = new AtomicBoolean(false);
  private final Runnable actorTask;

  /**
   * @param actorTask the domain logic to execute on the Actor thread when signaled
   */
  public ActorWorkSignaler(final Runnable actorTask) {
    this.actorTask = actorTask;
  }

  /**
   * Signals the Actor to execute the configured task.
   *
   * <p><b>Thread Safety:</b> Lock-free and safe to call concurrently from any thread.
   *
   * @param actor the ActorControl used to submit the job to the Actor's mailbox
   */
  public void signal(final ActorControl actor) {
    if (isScheduled.compareAndSet(false, true)) {
      actor.submit(this::executeTask);
    }
  }

  private void executeTask() {
    // Reset the flag BEFORE running the domain logic.
    // This ensures any new signals arriving during execution will trigger a subsequent run.
    isScheduled.set(false);
    actorTask.run();
  }
}
