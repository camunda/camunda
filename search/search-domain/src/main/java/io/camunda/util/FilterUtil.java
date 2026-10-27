/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import io.camunda.search.filter.Operation;
import java.util.List;
import java.util.function.Function;

public final class FilterUtil {

  private FilterUtil() {}

  public static <T> Operation<T> mapDefaultToOperation(final List<T> values) {
    if (values.isEmpty()) {
      return null;
    }
    return values.size() == 1 ? Operation.eq(values.getFirst()) : Operation.in(values);
  }

  @SafeVarargs
  public static <T> Operation<T> mapDefaultToOperation(final T value, final T... values) {
    return mapDefaultToOperation(e -> e, value, values);
  }

  @SafeVarargs
  public static <T, R> Operation<T> mapDefaultToOperation(
      final Function<R, T> typeMapper, final R value, final R... values) {
    return mapDefaultToOperation(CollectionUtil.collectValues(typeMapper, value, values));
  }
}
