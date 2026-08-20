/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.search.entities.ValueTypeEnum;
import org.junit.jupiter.api.Test;

public class ValueTypeUtilTest {

  @Test
  public void shouldReturnNullForNullValue() {
    assertThat(ValueTypeUtil.getValueType(null)).isEqualTo(ValueTypeEnum.NULL);
  }

  @Test
  public void shouldReturnNullForStringNullValue() {
    assertThat(ValueTypeUtil.getValueType("null")).isEqualTo(ValueTypeEnum.NULL);
  }

  @Test
  public void shouldReturnStringForNonNumericString() {
    assertThat(ValueTypeUtil.getValueType("non-numeric")).isEqualTo(ValueTypeEnum.STRING);
  }

  @Test
  public void shouldReturnLongForIntegerString() {
    assertThat(ValueTypeUtil.getValueType("123456")).isEqualTo(ValueTypeEnum.LONG);
  }

  @Test
  public void shouldReturnLongForBooleanLikeIntegerString() {
    assertThat(ValueTypeUtil.getValueType("1")).isEqualTo(ValueTypeEnum.LONG);
  }

  @Test
  public void shouldReturnStringForIntegerStringWithQuotes() {
    assertThat(ValueTypeUtil.getValueType("\"123456\"")).isEqualTo(ValueTypeEnum.STRING);
  }

  @Test
  public void shouldReturnDoubleForDoubleString() {
    assertThat(ValueTypeUtil.getValueType("123.456")).isEqualTo(ValueTypeEnum.DOUBLE);
  }

  @Test
  public void shouldReturnStringForDoubleStringWithQuotes() {
    assertThat(ValueTypeUtil.getValueType("\"123.456\"")).isEqualTo(ValueTypeEnum.STRING);
  }

  @Test
  public void shouldReturnLongForLongValue() {
    assertThat(ValueTypeUtil.getValueType(123456L)).isEqualTo(ValueTypeEnum.LONG);
  }

  @Test
  public void shouldReturnDoubleForDoubleValue() {
    assertThat(ValueTypeUtil.getValueType(123.456)).isEqualTo(ValueTypeEnum.DOUBLE);
  }

  @Test
  public void shouldReturnBooleanForBooleanValue() {
    assertThat(ValueTypeUtil.getValueType(true)).isEqualTo(ValueTypeEnum.BOOLEAN);
  }

  @Test
  public void shouldReturnBooleanForBooleanStringValue() {
    assertThat(ValueTypeUtil.getValueType("true")).isEqualTo(ValueTypeEnum.BOOLEAN);
  }

  @Test
  public void shouldReturnStringForBooleanStringValueWithQuotes() {
    assertThat(ValueTypeUtil.getValueType("\"true\"")).isEqualTo(ValueTypeEnum.STRING);
  }

  @Test
  public void shouldMapStringToString() {
    assertThat(ValueTypeUtil.mapValueType("test", ValueTypeEnum.STRING)).isEqualTo("test");
  }

  @Test
  public void shouldMapStringToLong() {
    assertThat(ValueTypeUtil.mapValueType("123456", ValueTypeEnum.LONG)).isEqualTo(123456L);
  }

  @Test
  public void shouldMapBooleanlikeStringToLong() {
    assertThat(ValueTypeUtil.mapValueType("123456", ValueTypeEnum.LONG)).isEqualTo(123456L);
  }

  @Test
  public void shouldMapStringToDouble() {
    assertThat(ValueTypeUtil.mapValueType("123.456", ValueTypeEnum.DOUBLE)).isEqualTo(123.456);
  }

  @Test
  public void shouldMapStringToBoolean() {
    assertThat(ValueTypeUtil.mapValueType("true", ValueTypeEnum.BOOLEAN)).isEqualTo("true");
  }

  @Test
  public void shouldMapLongToLong() {
    assertThat(ValueTypeUtil.mapValueType(123456L, ValueTypeEnum.LONG)).isEqualTo(123456L);
  }

  @Test
  public void shouldMapIntegerToLong() {
    assertThat(ValueTypeUtil.mapValueType(123456, ValueTypeEnum.LONG)).isEqualTo(123456L);
  }

  @Test
  public void shouldMapDoubleToDouble() {
    assertThat(ValueTypeUtil.mapValueType(123.456, ValueTypeEnum.DOUBLE)).isEqualTo(123.456);
  }

  @Test
  public void shouldReturnNullForUnsupportedType() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> ValueTypeUtil.getValueType(new Object()));
  }
}
