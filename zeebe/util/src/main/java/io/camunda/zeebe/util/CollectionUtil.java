/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.function.Function;

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
}
