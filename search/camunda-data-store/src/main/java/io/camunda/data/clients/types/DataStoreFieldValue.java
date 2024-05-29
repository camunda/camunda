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

public final class DataStoreFieldValue {

  public static final DataStoreFieldValue NULL =
      new DataStoreFieldValue(DataStoreFieldValueType.Null, null);
  public static final DataStoreFieldValue TRUE =
      new DataStoreFieldValue(DataStoreFieldValueType.Boolean, Boolean.TRUE);
  public static final DataStoreFieldValue FALSE =
      new DataStoreFieldValue(DataStoreFieldValueType.Boolean, Boolean.FALSE);

  private final DataStoreFieldValueType type;
  private final Object value;

  private DataStoreFieldValue(final DataStoreFieldValueType type, final Object value) {
    this.type = type;
    this.value = type == DataStoreFieldValueType.Null ? null : value;
  }

  public boolean isNull() {
    return type == DataStoreFieldValueType.Null;
  }

  public boolean isDouble() {
    return type == DataStoreFieldValueType.Double;
  }

  public double doubleValue() {
    return (double) value;
  }

  public boolean isInteger() {
    return type == DataStoreFieldValueType.Integer;
  }

  public int intValue() {
    return (int) value;
  }

  public boolean isLong() {
    return type == DataStoreFieldValueType.Long;
  }

  public long longValue() {
    return (long) value;
  }

  public boolean isBoolean() {
    return type == DataStoreFieldValueType.Boolean;
  }

  public boolean booleanValue() {
    return (boolean) value;
  }

  public boolean isString() {
    return type == DataStoreFieldValueType.String;
  }

  public String stringValue() {
    return (String) value;
  }

  public enum DataStoreFieldValueType {
    Double,
    Integer,
    Long,
    Boolean,
    String,
    Null
  }

  public static DataStoreFieldValue of(int value) {
    return new DataStoreFieldValue(DataStoreFieldValueType.Integer, value);
  }

  public static DataStoreFieldValue of(long value) {
    return new DataStoreFieldValue(DataStoreFieldValueType.Long, value);
  }

  public static DataStoreFieldValue of(double value) {
    return new DataStoreFieldValue(DataStoreFieldValueType.Double, value);
  }

  public static DataStoreFieldValue of(boolean value) {
    return value ? TRUE : FALSE;
  }

  public static DataStoreFieldValue of(String value) {
    return new DataStoreFieldValue(DataStoreFieldValueType.String, value);
  }

  public static <T> List<DataStoreFieldValue> of(
      final List<T> values, final Function<T, DataStoreFieldValue> mapper) {
    return values.stream().map(mapper).toList();
  }
}
