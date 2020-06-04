/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicPosition {
  private final AtomicLong position;

  public AtomicPosition() {
    this(0);
  }

  public AtomicPosition(final long position) {
    this.position = new AtomicLong(position);
  }

  public void reset() {
    set(-1);
  }

  public long get() {
    return position.get();
  }

  public void set(final long value) {
    position.set(value);
  }

  public boolean proposeMaxOrdered(final long newValue) {
    boolean updated = false;

    while (!updated) {
      final long currentPosition = position.get();
      if (currentPosition < newValue) {
        updated = position.compareAndSet(currentPosition, newValue);
      } else {
        return false;
      }
    }

    return true;
  }
}
