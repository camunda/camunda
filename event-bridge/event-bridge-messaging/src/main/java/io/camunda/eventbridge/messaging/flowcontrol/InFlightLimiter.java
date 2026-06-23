/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.flowcontrol;

import java.util.concurrent.atomic.AtomicInteger;

public final class InFlightLimiter implements FlowControl {

  private final AtomicInteger inFlightEntries = new AtomicInteger(0);
  private final int maxInFlightEntries;

  public InFlightLimiter(final int maxInFlightEntries) {
    this.maxInFlightEntries = maxInFlightEntries;
  }

  @Override
  public boolean tryAcquire(final int entryCount) {
    while (true) {
      final int current = inFlightEntries.get();
      if (current + entryCount > maxInFlightEntries) {
        return false;
      }
      if (inFlightEntries.compareAndSet(current, current + entryCount)) {
        return true;
      }
      // CAS failed — another won. Retry with fresh value.
    }
  }

  @Override
  public void release(final int entryCount) {
    inFlightEntries.addAndGet(-entryCount);
  }
}
