/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util;

import static io.camunda.zeebe.util.ObjectWriterFactory.getDefaultJsonObjectWriter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.springframework.util.unit.DataSize;

public class ObjectWriterFactoryTest {

  @Test
  public void shouldReturnNonNullDefaultObjectWriter() {
    // when
    final ObjectWriter actual = getDefaultJsonObjectWriter();

    // then
    assertThat(actual).isNotNull();
  }

  @Test
  public void shouldReturnObjectWriterWithPrettyPrintingEnabled() {
    // given
    final Map<String, String> objectToSerialize = new HashMap<>();
    objectToSerialize.put("field1", "value1");
    objectToSerialize.put("field2", "value2");

    String actual;
    // when
    try {
      actual = getDefaultJsonObjectWriter().writeValueAsString(objectToSerialize);
    } catch (final JsonProcessingException e) {
      fail(e.getMessage());
      actual = "";
    }

    // then
    assertThat(actual)
        .withFailMessage("Should be pretty printed and therefore contain line breaks")
        .contains(System.lineSeparator());
  }

  @Test
  public void shouldReturnObjectWriterThatWritesDurationAsISO8601() {
    // given
    final Duration objectToSerialize = Duration.ofSeconds(30);

    String actual;
    // when
    try {
      actual = getDefaultJsonObjectWriter().writeValueAsString(objectToSerialize);
    } catch (final JsonProcessingException e) {
      fail(e.getMessage());
      actual = "";
    }

    // then
    assertThat(actual)
        .describedAs("Serialized form of Duration")
        .isEqualTo("\"" + Duration.ofSeconds(30).toString() + "\"");
  }

  @Test
  public void shouldReturnObjectWriterThatWritesDataSizesInMB() {
    // given
    final DataSize objectToSerialize = DataSize.ofMegabytes(512);

    String actual;
    // when
    try {
      actual = getDefaultJsonObjectWriter().writeValueAsString(objectToSerialize);
    } catch (final JsonProcessingException e) {
      fail(e.getMessage());
      actual = "";
    }

    // then
    assertThat(actual).describedAs("Serialized form of DataSize").isEqualTo("\"512MB\"");
  }

  @Test
  public void shouldReturnObjectWriterThatWritesDataSizesInKB() {
    // given
    final DataSize objectToSerialize = DataSize.ofKilobytes(512);

    String actual;
    // when
    try {
      actual = getDefaultJsonObjectWriter().writeValueAsString(objectToSerialize);
    } catch (final JsonProcessingException e) {
      fail(e.getMessage());
      actual = "";
    }

    // then
    assertThat(actual).describedAs("Serialized form of DataSize").isEqualTo("\"512KB\"");
  }

  @Test
  public void shouldReturnObjectWriterThatWritesDataSizesInBytes() {
    // given
    final DataSize objectToSerialize = DataSize.ofBytes(512);

    String actual;
    // when
    try {
      actual = getDefaultJsonObjectWriter().writeValueAsString(objectToSerialize);
    } catch (final JsonProcessingException e) {
      fail(e.getMessage());
      actual = "";
    }

    // then
    assertThat(actual).describedAs("Serialized form of DataSize").isEqualTo("\"512B\"");
  }
}
