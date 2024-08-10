/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

public interface NumberThrottleable {

  void throttle();

  void reset();

  int get();

  int getOriginal();

  class DivideNumberThrottle implements NumberThrottleable {

    private final int originalSize;
    private final int throttleFactor;
    private int changedSizeUsed;
    private int currentSize;

    public DivideNumberThrottle(final int originalSize) {
      this(originalSize, 2);
    }

    public DivideNumberThrottle(final int originalSize, final int throttleFactor) {
      this.originalSize = originalSize;
      this.throttleFactor = throttleFactor;
      this.currentSize = originalSize;
    }

    @Override
    public void throttle() {
      changedSizeUsed = 0;
      currentSize = currentSize / throttleFactor;
      if (currentSize < 2) {
        currentSize = 1;
      }
    }

    @Override
    public void reset() {
      changedSizeUsed = 0;
      currentSize = originalSize;
    }

    @Override
    public int get() {
      if (currentSize < originalSize) {
        changedSizeUsed += 1;
      }
      if (canSetToOriginalValue()) {
        reset();
      }
      return currentSize;
    }

    @Override
    public int getOriginal() {
      return originalSize;
    }

    private boolean canSetToOriginalValue() {
      // Consider
      // - used throttled value (currentSize),
      // - how often it was used (changedSizeUsed)
      // - and a coefficient value (2)
      return changedSizeUsed * currentSize * 2 > originalSize;
    }
  }
}
