/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

interface GaugeState {

  void set(long value);

  long get();

  long updateAndGet(LongUnaryOperator updateFunction);

  static GaugeState from(final AtomicLong state) {
    return new GaugeState() {
      @Override
      public void set(final long value) {
        state.set(value);
      }

      @Override
      public long get() {
        return state.get();
      }

      @Override
      public long updateAndGet(final LongUnaryOperator updateFunction) {
        return state.updateAndGet(updateFunction);
      }
    };
  }
}
