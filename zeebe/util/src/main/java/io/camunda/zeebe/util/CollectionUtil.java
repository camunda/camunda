/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import io.camunda.zeebe.util.collection.Tuple;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class CollectionUtil {
  public static <T, V> boolean containsDuplicates(
      final Collection<T> collection, final Function<T, V> projection) {
    final var set = new HashSet<V>();
    for (final var t : collection) {
      final var v = projection.apply(t);
      if (set.contains(v)) {
        return true;
      }
      set.add(v);
    }
    return false;
  }

  public static <T> boolean containsDuplicates(final Collection<T> collection) {
    return containsDuplicates(collection, Function.identity());
  }

  /**
   * Zips two iterables into a collection of tuples, pairing elements by their iteration order.
   * Stops as soon as the shorter of the two iterables is exhausted; excess elements of the longer
   * iterable are ignored.
   *
   * @param left the left iterable
   * @param right the right iterable
   * @return a collection of tuples, one per pair of elements, in iteration order
   */
  public static <T, V> Iterator<Tuple<T, V>> zip(final Iterator<T> left, final Iterator<V> right) {
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return left.hasNext() && right.hasNext();
      }

      @Override
      public Tuple<T, V> next() {
        return new Tuple<>(left.next(), right.next());
      }
    };
  }

  /**
   * Zips two collections into a stream of tuples, pairing elements by their iteration order. Stops
   * as soon as the shorter of the two collections is exhausted; excess elements of the longer
   * collection are ignored.
   *
   * @param left the left collection
   * @param right the right collection
   * @return a Stream of tuples, one per pair of elements, in iteration order
   */
  public static <T, V> Stream<Tuple<T, V>> zipAsStream(
      final Collection<T> left, final Collection<V> right) {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(
            zip(left.iterator(), right.iterator()), Spliterator.ORDERED),
        false);
  }

  /**
   * Zips two iterators into a stream of tuples, pairing elements by their iteration order. Stops as
   * soon as the shorter of the two iterators is exhausted; excess elements of the longer iterator
   * are ignored.
   *
   * @param left the left iterator
   * @param right the right iterator
   * @return a Stream of tuples, one per pair of elements, in iteration order
   */
  public static <T, V> Stream<Tuple<T, V>> zipAsStream(
      final Iterator<T> left, final Iterator<V> right) {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(zip(left, right), Spliterator.ORDERED), false);
  }
}
