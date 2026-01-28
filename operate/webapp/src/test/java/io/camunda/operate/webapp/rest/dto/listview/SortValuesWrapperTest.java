/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.Tuple;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

public class SortValuesWrapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testConvertSortValuesString() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString("testString"), String.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo("testString");
    assertThat(result[0].getClass()).isEqualTo(String.class);
  }

  @Test
  public void testConvertSortValuesLong() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(Long.MAX_VALUE), Long.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(Long.MAX_VALUE);
    assertThat(result[0].getClass()).isEqualTo(Long.class);
  }

  @Test
  public void testConvertSortValuesInteger() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(123), Integer.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(123);
    assertThat(result[0].getClass()).isEqualTo(Integer.class);
  }

  @Test
  public void testConvertSortValuesShort() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(Short.MIN_VALUE), Short.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(Short.MIN_VALUE);
    assertThat(result[0].getClass()).isEqualTo(Short.class);
  }

  @Test
  public void testConvertSortValuesByte() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(Byte.MIN_VALUE), Byte.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(Byte.MIN_VALUE);
    assertThat(result[0].getClass()).isEqualTo(Byte.class);
  }

  @Test
  public void testConvertSortValuesDouble() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(Double.MIN_VALUE), Double.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(Double.MIN_VALUE);
    assertThat(result[0].getClass()).isEqualTo(Double.class);
  }

  @Test
  public void testConvertSortValuesFloat() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(Float.MIN_VALUE), Float.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(Float.MIN_VALUE);
    assertThat(result[0].getClass()).isEqualTo(Float.class);
  }

  @Test
  public void testConvertSortValuesBoolean() throws JsonProcessingException {
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(false), Boolean.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(false);
    assertThat(result[0].getClass()).isEqualTo(Boolean.class);
  }

  @Test
  public void testConvertSortValuesBigInteger() throws JsonProcessingException {

    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(BigInteger.TWO), BigInteger.class)
    };

    final Object[] result = SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0]).isEqualTo(BigInteger.TWO);
    assertThat(result[0].getClass()).isEqualTo(BigInteger.class);
  }

  @Test
  public void testConvertSortValuesBadType() throws JsonProcessingException {
    final Tuple<String, String> tupleVal = new Tuple<>("left", "right");
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(tupleVal), Tuple.class)
    };

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper));
  }

  @Test
  public void testConvertSortValuesRecordType() throws JsonProcessingException {
    record Result(String id) {}
    final SortValuesWrapper[] sortValuesWrappers = {
      new SortValuesWrapper(objectMapper.writeValueAsString(new Result("foo")), Result.class)
    };

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> SortValuesWrapper.convertSortValues(sortValuesWrappers, objectMapper));
  }

  @Test
  public void testCreateFromString() throws JsonProcessingException {
    final Object[] sortValues = {"testString"};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString("testString"));
    assertThat(result[0].getValueType()).isEqualTo(String.class);
  }

  @Test
  public void testCreateFromLong() throws JsonProcessingException {
    final Object[] sortValues = {Long.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Long.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Long.class);
  }

  @Test
  public void testCreateFromInteger() throws JsonProcessingException {
    final Object[] sortValues = {Integer.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Integer.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Integer.class);
  }

  @Test
  public void testCreateFromShort() throws JsonProcessingException {
    final Object[] sortValues = {Short.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Short.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Short.class);
  }

  @Test
  public void testCreateFromByte() throws JsonProcessingException {
    final Object[] sortValues = {Byte.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Byte.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Byte.class);
  }

  @Test
  public void testCreateFromDouble() throws JsonProcessingException {
    final Object[] sortValues = {Double.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Double.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Double.class);
  }

  @Test
  public void testCreateFromFloat() throws JsonProcessingException {
    final Object[] sortValues = {Float.MAX_VALUE};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(Float.MAX_VALUE));
    assertThat(result[0].getValueType()).isEqualTo(Float.class);
  }

  @Test
  public void testCreateFromBoolean() throws JsonProcessingException {
    final Object[] sortValues = {true};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(true));
    assertThat(result[0].getValueType()).isEqualTo(Boolean.class);
  }

  @Test
  public void testCreateFromBigInteger() throws JsonProcessingException {
    final Object[] sortValues = {BigInteger.TWO};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(BigInteger.TWO));
    assertThat(result[0].getValueType()).isEqualTo(BigInteger.class);
  }

  @Test
  public void testCreateFromTypeNotAllowedInDeserialization() throws JsonProcessingException {
    final Tuple<String, String> tupleVal = new Tuple<>("left", "right");
    final Object[] sortValues = {tupleVal};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(tupleVal));
    assertThat(result[0].getValueType()).isEqualTo(Tuple.class);
  }

  @Test
  public void testCreateFromRecordNotAllowedInDeserialization() throws JsonProcessingException {
    record Result(String id) {}
    final var recordVal = new Result("id");
    final Object[] sortValues = {recordVal};

    final SortValuesWrapper[] result = SortValuesWrapper.createFrom(sortValues, objectMapper);

    assertThat(result.length).isEqualTo(1);
    assertThat(result[0].getValue()).isEqualTo(objectMapper.writeValueAsString(recordVal));
    assertThat(result[0].getValueType()).isEqualTo(Result.class);
  }
}
