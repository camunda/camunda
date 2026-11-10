/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import java.time.Duration;

/**
 * Computes how long a poller waits before its next poll. After a poll that found work it polls
 * again soon. Each poll that finds nothing doubles the wait, up to a maximum. This keeps the load
 * on the database low when the mailbox is empty, and the delay low when it is busy.
 *
 * <p>Not thread-safe. Use one instance for each poller.
 */
public final class AdaptiveBackoff {

  private final Duration min;
  private final Duration max;
  private Duration current;

  public AdaptiveBackoff(final Duration min, final Duration max) {
    this.min = min;
    this.max = max;
    current = min;
  }

  /** The poll found work. Returns the wait until the next poll. */
  public Duration onWork() {
    current = min;
    return current;
  }

  /** The poll found nothing. Returns the wait until the next poll. */
  public Duration onIdle() {
    final var doubled = current.multipliedBy(2);
    current = doubled.compareTo(max) > 0 ? max : doubled;
    return current;
  }
}
