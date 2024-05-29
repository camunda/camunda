/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.types;

import java.util.List;
import java.util.function.Function;

public final class DataStoreTypedValue {

  public static final DataStoreTypedValue NULL =
      new DataStoreTypedValue(DataStoreValueType.Null, null);
  public static final DataStoreTypedValue TRUE =
      new DataStoreTypedValue(DataStoreValueType.Boolean, Boolean.TRUE);
  public static final DataStoreTypedValue FALSE =
      new DataStoreTypedValue(DataStoreValueType.Boolean, Boolean.FALSE);

  private final DataStoreValueType type;
  private final Object value;

  private DataStoreTypedValue(final DataStoreValueType type, final Object value) {
    this.type = type;
    this.value = type == DataStoreValueType.Null ? null : value;
  }

  public boolean isNull() {
    return type == DataStoreValueType.Null;
  }

  public boolean isDouble() {
    return type == DataStoreValueType.Double;
  }

  public double doubleValue() {
    return (double) value;
  }

  public boolean isInteger() {
    return type == DataStoreValueType.Integer;
  }

  public int intValue() {
    return (int) value;
  }

  public boolean isLong() {
    return type == DataStoreValueType.Long;
  }

  public long longValue() {
    return (long) value;
  }

  public boolean isBoolean() {
    return type == DataStoreValueType.Boolean;
  }

  public boolean booleanValue() {
    return (boolean) value;
  }

  public boolean isString() {
    return type == DataStoreValueType.String;
  }

  public String stringValue() {
    return (String) value;
  }

  public enum DataStoreValueType {
    Double,
    Integer,
    Long,
    Boolean,
    String,
    Null
  }

  public static DataStoreTypedValue of(int value) {
    return new DataStoreTypedValue(DataStoreValueType.Integer, value);
  }

  public static DataStoreTypedValue of(long value) {
    return new DataStoreTypedValue(DataStoreValueType.Long, value);
  }

  public static DataStoreTypedValue of(double value) {
    return new DataStoreTypedValue(DataStoreValueType.Double, value);
  }

  public static DataStoreTypedValue of(boolean value) {
    return value ? TRUE : FALSE;
  }

  public static DataStoreTypedValue of(String value) {
    return new DataStoreTypedValue(DataStoreValueType.String, value);
  }

  public static <T> List<DataStoreTypedValue> of(
      final List<T> values, final Function<T, DataStoreTypedValue> mapper) {
    return values.stream().map(mapper).toList();
  }
}
