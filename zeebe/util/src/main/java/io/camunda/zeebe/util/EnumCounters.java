/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.concurrent.atomic.LongAdder;

/** Thread-safe counters that only ever increase, one for each constant of an enum. */
public final class EnumCounters<K extends Enum<K>> {

  private final LongAdder[] counts;

  public EnumCounters(final Class<K> keyType) {
    counts = new LongAdder[keyType.getEnumConstants().length];
    for (int i = 0; i < counts.length; i++) {
      counts[i] = new LongAdder();
    }
  }

  public void increment(final K key) {
    counts[key.ordinal()].increment();
  }

  public void add(final K key, final long amount) {
    counts[key.ordinal()].add(amount);
  }

  public long get(final K key) {
    return counts[key.ordinal()].sum();
  }
}
