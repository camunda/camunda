/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.CollectionUtil;
import java.util.List;

public record Operation<T>(Operator operator, List<T> values) {

  public Operation(final Operator operator, final T value) {
    this(operator, CollectionUtil.collectValuesAsList(value));
  }

  public static <T> Operation<T> of(final Operator operator, final T value) {
    return new Operation<>(operator, value);
  }

  public static <T> Operation<T> eq(final T value) {
    return new Operation<>(Operator.EQUALS, value);
  }

  public static <T> Operation<T> neq(final T value) {
    return new Operation<>(Operator.NOT_EQUALS, value);
  }

  public static <T> Operation<T> exists(final boolean value) {
    return new Operation<>(value ? Operator.EXISTS : Operator.NOT_EXISTS, null);
  }

  public static <T> Operation<T> gt(final T value) {
    return new Operation<>(Operator.GREATER_THAN, value);
  }

  public static <T> Operation<T> gte(final T value) {
    return new Operation<>(Operator.GREATER_THAN_EQUALS, value);
  }

  public static <T> Operation<T> lt(final T value) {
    return new Operation<>(Operator.LOWER_THAN, value);
  }

  public static <T> Operation<T> lte(final T value) {
    return new Operation<>(Operator.LOWER_THAN_EQUALS, value);
  }

  @SafeVarargs
  public static <T> Operation<T> in(final T... values) {
    return new Operation<>(Operator.IN, List.of(values));
  }

  public static <T> Operation<T> in(final List<T> values) {
    return new Operation<>(Operator.IN, values);
  }

  public static <T> Operation<T> like(final T value) {
    return new Operation<>(Operator.LIKE, value);
  }

  public T value() {
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.getFirst();
  }

  @Override
  public List<T> values() {
    return values;
  }
}
