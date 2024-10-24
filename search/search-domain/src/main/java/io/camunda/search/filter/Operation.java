/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import java.util.List;

public record Operation<T>(Operator operator, List<T> values) {

  public enum Operator {
    EQUALS("eq"),
    NOT_EQUALS("neq"),
    GREATER_THAN("gt"),
    GREATER_THAN_EQUALS("gte"),
    LOWER_THAN("lt"),
    LOWER_THAN_EQUALS("lte"),
    IN("in"),
    LIKE("like");

    private final String value;

    Operator(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public Operation(final Operator operator, final T value) {
    this(operator, List.of(value));
  }

  public Operation(final Operator operator, final List<T> values) {
    this.operator = operator;
    this.values = values;
  }

  public static <T> Operation<T> of(Operator operator, T value) {
    return new Operation<>(operator, value);
  }

  public static <T> Operation<T> eq(T value) {
    return new Operation<>(Operator.EQUALS, value);
  }

  public static <T> Operation<T> neq(T value) {
    return new Operation<>(Operator.NOT_EQUALS, value);
  }

  public static <T> Operation<T> gt(T value) {
    return new Operation<>(Operator.GREATER_THAN, value);
  }

  public static <T> Operation<T> gte(T value) {
    return new Operation<>(Operator.GREATER_THAN_EQUALS, value);
  }

  public static <T> Operation<T> lt(T value) {
    return new Operation<>(Operator.LOWER_THAN, value);
  }

  public static <T> Operation<T> lte(T value) {
    return new Operation<>(Operator.LOWER_THAN_EQUALS, value);
  }

  @SafeVarargs
  public static <T> Operation<T> in(T... values) {
    return new Operation<>(Operator.IN, List.of(values));
  }

  public static <T> Operation<T> in(List<T> values) {
    return new Operation<>(Operator.IN, values);
  }

  public static <T> Operation<T> like(T value) {
    return new Operation<>(Operator.LIKE, value);
  }

  public T value() {
    return values.getFirst();
  }

  public List<T> values() {
    return values;
  }
}
