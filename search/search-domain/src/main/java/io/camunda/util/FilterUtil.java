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
import org.apache.commons.lang3.ObjectUtils;

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

  /**
   * Unlike {@link ObjectUtils#isNotEmpty}, a {@link String} is considered present as soon as it is
   * non-null — an explicitly supplied empty string ("") is a meaningful exact-match criterion, not
   * an absent one. Collections/maps/arrays keep the generic null-or-empty-means-absent semantics,
   * since some filter fields are normalized to an empty collection rather than left null when
   * unset.
   */
  public static boolean hasAnyNonEmpty(final Object... values) {
    for (final var value : values) {
      if (value instanceof String || ObjectUtils.isNotEmpty(value)) {
        return true;
      }
    }
    return false;
  }
}
