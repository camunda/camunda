/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.types;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final record TypedValue(ValueType type, Object value) {

  public static final TypedValue NULL = new TypedValue(ValueType.NULL, null);
  public static final TypedValue TRUE = new TypedValue(ValueType.BOOLEAN, Boolean.TRUE);
  public static final TypedValue FALSE = new TypedValue(ValueType.BOOLEAN, Boolean.FALSE);

  public TypedValue(final ValueType type, final Object value) {
    if (value == null && type != ValueType.NULL) {
      throw new IllegalArgumentException("Expected non-null value, for value type " + type.name());
    }

    this.type = type;
    this.value = type == ValueType.NULL ? null : value;
  }

  public boolean isNull() {
    return type == ValueType.NULL;
  }

  public boolean isDouble() {
    return type == ValueType.DOUBLE;
  }

  public double doubleValue() {
    return (double) value;
  }

  public boolean isShort() {
    return type == ValueType.SHORT;
  }

  public short shortValue() {
    return (short) value;
  }

  public boolean isInteger() {
    return type == ValueType.INTEGER;
  }

  public int intValue() {
    return (int) value;
  }

  public boolean isLong() {
    return type == ValueType.LONG;
  }

  public long longValue() {
    return (long) value;
  }

  public boolean isBoolean() {
    return type == ValueType.BOOLEAN;
  }

  public boolean booleanValue() {
    return (boolean) value;
  }

  public boolean isString() {
    return type == ValueType.STRING;
  }

  public String stringValue() {
    return (String) value;
  }

  public static TypedValue of(final short value) {
    return new TypedValue(ValueType.SHORT, value);
  }

  public static TypedValue of(final int value) {
    return new TypedValue(ValueType.INTEGER, value);
  }

  public static TypedValue of(final long value) {
    return new TypedValue(ValueType.LONG, value);
  }

  public static TypedValue of(final double value) {
    return new TypedValue(ValueType.DOUBLE, value);
  }

  public static TypedValue of(final boolean value) {
    return value ? TRUE : FALSE;
  }

  public static TypedValue of(final String value) {
    if (value == null) {
      return NULL;
    }

    return new TypedValue(ValueType.STRING, value);
  }

  public static <T> List<TypedValue> of(
      final List<T> values, final Function<T, TypedValue> mapper) {
    if (values == null) {
      throw new IllegalArgumentException("Expected non-null values collection, for typed values.");
    }
    return values.stream().map(mapper).collect(Collectors.toList());
  }

  public static TypedValue toTypedValue(final Object value) {
    if (value == null) {
      return NULL;
    } else if (value instanceof TypedValue) {
      return (TypedValue) value;
    } else if (String.class.isAssignableFrom(value.getClass())) {
      return of((String) value);
    } else if (Integer.class.isAssignableFrom(value.getClass())) {
      return of((Integer) value);
    } else if (Long.class.isAssignableFrom(value.getClass())) {
      return of((Long) value);
    } else if (Double.class.isAssignableFrom(value.getClass())) {
      return of((Double) value);
    } else if (Boolean.class.isAssignableFrom(value.getClass())) {
      return of((Boolean) value);
    }
    throw new IllegalArgumentException("Non-supported type: " + value.getClass());
  }

  public enum ValueType {
    DOUBLE,
    SHORT,
    INTEGER,
    LONG,
    BOOLEAN,
    STRING,
    NULL
  }
}
