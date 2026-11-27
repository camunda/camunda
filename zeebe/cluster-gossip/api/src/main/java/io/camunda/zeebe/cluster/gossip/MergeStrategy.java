/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip;

import java.util.Objects;
import java.util.function.BinaryOperator;

/**
 * Resolves concurrent writes to a {@link GlobalKey}.
 *
 * <p>Updates arrive in any order, more than once and along different paths, so every member only
 * converges to the same value if merging is commutative, associative and idempotent.
 *
 * @param <T> the type of the value
 */
public sealed interface MergeStrategy<T> {

  /**
   * The gossiper attaches a hidden write timestamp and the writer's member id to every write. The
   * write with the highest timestamp wins; ties go to the higher member id by {@link
   * io.atomix.cluster.MemberId#ID_COMPARATOR}, and remaining ties to the greater encoded value, so
   * every member picks the same winner. A member stamps each write with a timestamp higher than the
   * one it holds for the key, so its own write always replaces its local value.
   *
   * <p>The timestamp is wall-clock time, so under clock skew the member with the faster clock wins.
   */
  static <T> MergeStrategy<T> lastWriterWins() {
    return new LastWriterWins<>();
  }

  /**
   * Merges values with the given function, which must be commutative, associative and idempotent.
   * Any ordering the merge needs, such as a version, must be part of the value.
   */
  static <T> MergeStrategy<T> merging(final BinaryOperator<T> merge) {
    return new Merging<>(merge);
  }

  /** See {@link #lastWriterWins()}. */
  record LastWriterWins<T>() implements MergeStrategy<T> {}

  /** See {@link #merging(BinaryOperator)}. */
  record Merging<T>(BinaryOperator<T> merge) implements MergeStrategy<T> {

    public Merging {
      Objects.requireNonNull(merge, "merge");
    }
  }
}
