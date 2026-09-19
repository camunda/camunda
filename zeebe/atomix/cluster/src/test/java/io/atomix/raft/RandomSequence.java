/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the long operation sequences of the randomized raft tests from a seeded {@link Random}.
 *
 * <p>The sequences are derived from a single seed drawn from Hegel instead of being drawn element
 * by element: at ten thousand steps per sequence they overrun the engine's per-test-case data
 * budget, and these tests run with shrinking disabled anyway, so a shrinkable sequence would buy
 * nothing.
 */
final class RandomSequence {

  private RandomSequence() {}

  static <T> List<T> of(final Random random, final List<T> choices, final int size) {
    final var sequence = new ArrayList<T>(size);
    for (int i = 0; i < size; i++) {
      sequence.add(choices.get(random.nextInt(choices.size())));
    }
    return sequence;
  }
}
