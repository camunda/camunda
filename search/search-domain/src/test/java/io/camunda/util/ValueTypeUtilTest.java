/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.search.entities.ValueTypeEnum;
import org.junit.jupiter.api.Test;

public class ValueTypeUtilTest {

  @Test
  public void shouldReturnNullForNullValue() {
    assertEquals(ValueTypeEnum.NULL, ValueTypeUtil.getValueType(null));
  }

  @Test
  public void shouldReturnStringForNonNumericString() {
    assertEquals(ValueTypeEnum.STRING, ValueTypeUtil.getValueType("non-numeric"));
  }

  @Test
  public void shouldReturnLongForIntegerString() {
    assertEquals(ValueTypeEnum.LONG, ValueTypeUtil.getValueType("123456"));
  }

  @Test
  public void shouldReturnDoubleForDoubleString() {
    assertEquals(ValueTypeEnum.DOUBLE, ValueTypeUtil.getValueType("123.456"));
  }

  @Test
  public void shouldReturnLongForLongValue() {
    assertEquals(ValueTypeEnum.LONG, ValueTypeUtil.getValueType(123456L));
  }

  @Test
  public void shouldReturnDoubleForDoubleValue() {
    assertEquals(ValueTypeEnum.DOUBLE, ValueTypeUtil.getValueType(123.456));
  }

  @Test
  public void shouldReturnBooleanForBooleanValue() {
    assertEquals(ValueTypeEnum.BOOLEAN, ValueTypeUtil.getValueType(true));
  }

  @Test
  public void shouldMapStringToString() {
    assertEquals("test", ValueTypeUtil.mapValueType("test", ValueTypeEnum.STRING));
  }

  @Test
  public void shouldMapStringToLong() {
    assertEquals(123456L, ValueTypeUtil.mapValueType("123456", ValueTypeEnum.LONG));
  }

  @Test
  public void shouldMapStringToDouble() {
    assertEquals(123.456, ValueTypeUtil.mapValueType("123.456", ValueTypeEnum.DOUBLE));
  }

  @Test
  public void shouldMapStringToBoolean() {
    assertEquals("true", ValueTypeUtil.mapValueType("true", ValueTypeEnum.BOOLEAN));
  }

  @Test
  public void shouldMapLongToLong() {
    assertEquals(123456L, ValueTypeUtil.mapValueType(123456L, ValueTypeEnum.LONG));
  }

  @Test
  public void shouldMapIntegerToLong() {
    assertEquals(123456L, ValueTypeUtil.mapValueType(123456, ValueTypeEnum.LONG));
  }

  @Test
  public void shouldMapDoubleToDouble() {
    assertEquals(123.456, ValueTypeUtil.mapValueType(123.456, ValueTypeEnum.DOUBLE));
  }

  @Test
  public void shouldReturnNullForUnsupportedType() {
    assertThrows(IllegalArgumentException.class, () -> ValueTypeUtil.getValueType(new Object()));
  }
}
