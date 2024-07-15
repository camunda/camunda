/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import io.camunda.zeebe.util.StreamUtil.MinMaxCollector.MinMax;
import java.util.Comparator;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public final class StreamUtil {
  private StreamUtil() {}

  /**
   * Returns a collector that computes the minimum and maximum of a stream of elements according to
   * the provided comparator.
   */
  public static <T> Collector<T, MinMax<T>, MinMax<T>> minMax(final Comparator<T> comparator) {
    return new MinMaxCollector<>(comparator);
  }

  static final class MinMaxCollector<T> implements Collector<T, MinMax<T>, MinMax<T>> {
    private final Comparator<T> comparator;

    private MinMaxCollector(final Comparator<T> comparator) {
      this.comparator = comparator;
    }

    @Override
    public Supplier<MinMax<T>> supplier() {
      return MinMax::new;
    }

    @Override
    public BiConsumer<MinMax<T>, T> accumulator() {
      return (minMax, value) -> {
        if (minMax.min == null || comparator.compare(value, minMax.min) < 0) {
          minMax.min = value;
        }
        if (minMax.max == null || comparator.compare(value, minMax.max) > 0) {
          minMax.max = value;
        }
      };
    }

    @Override
    public BinaryOperator<MinMax<T>> combiner() {
      return (minMax1, minMax2) -> {
        if (comparator.compare(minMax1.min, minMax2.min) < 0) {
          minMax1.min = minMax2.min;
        }
        if (comparator.compare(minMax1.max, minMax2.max) > 0) {
          minMax1.max = minMax2.max;
        }
        return minMax1;
      };
    }

    @Override
    public Function<MinMax<T>, MinMax<T>> finisher() {
      return (minMax) -> minMax;
    }

    @Override
    public Set<Characteristics> characteristics() {
      return Set.of(Characteristics.IDENTITY_FINISH);
    }

    public static final class MinMax<T> {
      private T min;
      private T max;

      /**
       * @return the minimum value of the stream, or {@code null} if the stream was empty.
       */
      public T min() {
        return min;
      }

      /**
       * @return the maximum value of the stream, or {@code null} if the stream was empty.
       */
      public T max() {
        return max;
      }
    }
  }
}
