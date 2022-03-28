/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.broker.protocol;

import java.util.Objects;
import org.jeasy.random.randomizers.AbstractRandomizer;

/**
 * A fixed variant of the built-in {@link org.jeasy.random.randomizers.misc.EnumRandomizer}. While
 * this one allows excluding certain values, it unfortunately doesn't support doing so AND using the
 * randomizer deterministically. See issue https://github.com/j-easy/easy-random/issues/472
 *
 * @param <E> the type of the enumeration
 */
final class EnumRandomizer<E extends Enum<E>> extends AbstractRandomizer<E> {
  private final E[] values;

  EnumRandomizer(final long seed, final E[] values) {
    super(seed);
    this.values = Objects.requireNonNull(values, "must specify some enum values");
  }

  @Override
  public E getRandomValue() {
    if (values.length == 0) {
      return null;
    }

    final int index = random.nextInt(values.length);
    return values[index];
  }
}
