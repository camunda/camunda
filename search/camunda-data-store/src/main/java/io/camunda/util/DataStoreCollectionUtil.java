/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public abstract class DataStoreCollectionUtil {

  public static <A> List<A> withoutNull(final A[] items) {
    if (items != null) {
      return withoutNull(Arrays.asList(items));
    }
    return null;
  }

  public static <A> List<A> withoutNull(final Collection<A> items) {
    if (items != null) {
      return items.stream().filter(Objects::nonNull).toList();
    }
    return null;
  }

  public static <T> List<T> listAddAll(final List<T> list, final List<T> values) {
    final List<T> result;
    if (list == null) {
      result = Objects.requireNonNull(values);
    } else {
      result = new ArrayList<>(list);
      list.addAll(values);
    }
    return result;
  }

  public static <T> List<T> listAdd(final List<T> list, final T value, final T... values) {
    final List<T> result;

    if (list == null) {
      result = new ArrayList<>();
    } else {
      result = new ArrayList<>(list);
    }

    result.add(value);
    if (values.length > 0) {
      result.addAll(Arrays.asList(values));
    }
    return result;
  }
}
