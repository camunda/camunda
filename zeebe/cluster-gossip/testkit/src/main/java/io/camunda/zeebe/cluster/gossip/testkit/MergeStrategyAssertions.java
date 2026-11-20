/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.cluster.gossip.testkit;

import io.camunda.zeebe.cluster.gossip.GlobalKey;
import io.camunda.zeebe.cluster.gossip.MergeStrategy.LastWriterWins;
import io.camunda.zeebe.cluster.gossip.MergeStrategy.Merging;
import java.util.Arrays;
import java.util.List;
import java.util.function.BinaryOperator;

/**
 * Checks that a key's custom merge lets every member converge: it must be commutative, associative
 * and idempotent over the given samples. Values are compared by their encoded bytes, as the
 * gossiper does, so a codec that is not deterministic fails the check as well.
 */
public final class MergeStrategyAssertions {

  private MergeStrategyAssertions() {}

  /**
   * @throws AssertionError naming the first violated law and the values that violate it
   * @throws IllegalArgumentException if the key does not use a custom merge
   */
  public static <T> void assertConverges(final GlobalKey<T> key, final List<T> samples) {
    final BinaryOperator<T> merge =
        switch (key.mergeStrategy()) {
          case final Merging<T> merging -> merging.merge();
          case final LastWriterWins<T> ignored ->
              throw new IllegalArgumentException(
                  "Expected key '" + key.name() + "' to use a custom merge, but it uses LWW");
        };

    for (final T a : samples) {
      assertSame(key, "idempotent", merge.apply(a, a), a, a, a);
      for (final T b : samples) {
        assertSame(key, "commutative", merge.apply(a, b), merge.apply(b, a), a, b);
        for (final T c : samples) {
          assertSame(
              key,
              "associative",
              merge.apply(merge.apply(a, b), c),
              merge.apply(a, merge.apply(b, c)),
              a,
              b,
              c);
        }
      }
    }
  }

  @SafeVarargs
  private static <T> void assertSame(
      final GlobalKey<T> key,
      final String law,
      final T actual,
      final T expected,
      final T... inputs) {
    if (!Arrays.equals(key.codec().encode(actual), key.codec().encode(expected))) {
      throw new AssertionError(
          "Expected the merge of key '"
              + key.name()
              + "' to be "
              + law
              + ", but it is not for "
              + Arrays.toString(inputs)
              + ": got "
              + actual
              + " where "
              + expected
              + " was expected");
    }
  }
}
