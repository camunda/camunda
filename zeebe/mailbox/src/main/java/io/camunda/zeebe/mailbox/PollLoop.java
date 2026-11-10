/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls a step again and again, and waits between the calls as an {@link AdaptiveBackoff} says. It
 * drives an {@link InboxDrainer}, a {@link ResponseAwaiter} or a {@link MailboxCleaner}.
 *
 * <p>The next call is scheduled only after the current one ended, so calls of one loop never
 * overlap. A step that throws is logged and counts as idle. A loop can be stopped and started
 * again. A call that was scheduled before the stop does nothing.
 *
 * <p>Thread-safe.
 */
public final class PollLoop implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PollLoop.class);

  private final String name;
  private final Scheduler scheduler;
  private final AdaptiveBackoff backoff;
  private final BooleanSupplier step;
  private final Object lock = new Object();
  private boolean running;
  private long generation;

  /**
   * @param name appears in log messages
   * @param step does one poll. Returns true if it found work, so that the next poll comes soon.
   */
  public PollLoop(
      final String name,
      final Scheduler scheduler,
      final AdaptiveBackoff backoff,
      final BooleanSupplier step) {
    this.name = name;
    this.scheduler = scheduler;
    this.backoff = backoff;
    this.step = step;
  }

  /** Starts the loop with a first call soon. Has no effect if it is already running. */
  public void start() {
    synchronized (lock) {
      if (running) {
        return;
      }
      running = true;
      generation++;
      scheduleNext(generation, backoff.onWork());
    }
  }

  /** Stops the loop. A call that is running ends, and no further call starts. */
  @Override
  public void close() {
    synchronized (lock) {
      running = false;
    }
  }

  private void scheduleNext(final long forGeneration, final Duration delay) {
    try {
      scheduler.schedule(() -> runStep(forGeneration), delay);
    } catch (final RejectedExecutionException e) {
      running = false;
      LOG.debug("Poll loop {} stopped, because its scheduler is closed", name);
    }
  }

  private void runStep(final long forGeneration) {
    if (!isCurrent(forGeneration)) {
      return;
    }
    var foundWork = false;
    try {
      foundWork = step.getAsBoolean();
    } catch (final RuntimeException e) {
      LOG.warn("Poll loop {} failed", name, e);
    }
    synchronized (lock) {
      if (running && generation == forGeneration) {
        scheduleNext(forGeneration, foundWork ? backoff.onWork() : backoff.onIdle());
      }
    }
  }

  private boolean isCurrent(final long forGeneration) {
    synchronized (lock) {
      return running && generation == forGeneration;
    }
  }
}
