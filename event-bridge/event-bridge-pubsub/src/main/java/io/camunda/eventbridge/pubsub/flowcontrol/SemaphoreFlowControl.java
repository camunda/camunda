/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.pubsub.flowcontrol;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A flow control implementation that bounds the number of concurrent read operations.
 *
 * <p><b>Contract:</b> This implementation limits concurrent execution using a {@link Semaphore}. It
 * acquires exactly one permit per read operation, regardless of the requested permit count,
 * treating each invocation as a single concurrent task.
 */
public final class SemaphoreFlowControl implements FlowControl {

  private final Semaphore semaphore;

  public SemaphoreFlowControl(final Semaphore semaphore) {
    this.semaphore = semaphore;
  }

  @Override
  public boolean tryAcquire(final int permits) {
    return semaphore.tryAcquire(permits);
  }

  @Override
  public boolean tryAcquire(final int permits, final long timeout, final TimeUnit unit) {
    try {
      return semaphore.tryAcquire(permits, timeout, unit);
    } catch (final Exception ignore) {
      return false;
    }
  }

  @Override
  public void release(final int permits) {
    semaphore.release(permits);
  }
}
