/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.flowcontrol;

import java.util.concurrent.TimeUnit;

/**
 * Admission control for the outbound fetch path.
 *
 * <p><b>Contract:</b> Bounds the execution of physical read operations based on available capacity.
 * Implementations must be thread-safe.
 */
public interface FlowControl {

  /**
   * Attempts to acquire the specified number of permits immediately without blocking.
   *
   * @param permits the number of permits required
   * @return {@code true} if the permits were acquired, {@code false} otherwise
   */
  boolean tryAcquire(int permits);

  default boolean tryAcquire(final int permits, final long timeout, final TimeUnit unit) {
    throw new UnsupportedOperationException();
  }

  /**
   * Releases the specified number of permits back to the pool.
   *
   * <p><b>Contract:</b> Must be called in a {@code finally} block after the read operation
   * completes to ensure capacity is restored regardless of operation success or failure.
   *
   * @param permits the number of permits to release
   */
  void release(int permits);
}
