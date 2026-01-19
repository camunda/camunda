/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CursorTest {

  public static List<Arguments> provideExamples() {
    return List.of(
        Arguments.of("Null", null),
        Arguments.of("Primitives", new Object[] {1L, "null", 3.14, true, false, null})
        //        Arguments.of("Map", new Object[] {Map.of("key", "value")}),
        //        Arguments.of("Array", new Object[] {new Object[] {1L, 2L, 3L}})
        );
  }

  @ParameterizedTest(name = "{0}:{1}")
  @MethodSource("provideExamples")
  void shouldEncodeDecodeValues(final String ignored, final Object[] values) {
    final String encoded = Cursor.encode(values);
    final Object[] decoded = Cursor.decode(encoded);

    assertThat(decoded).usingRecursiveComparison().isEqualTo(values);
  }

  @Test
  void shouldEncodeToNullWithNullValues() {
    assertThat(Cursor.encode(null)).isNull();
  }

  @Test
  void shouldEncodeToNullWithEmptyValues() {
    assertThat(Cursor.encode(new Object[] {})).isNull();
  }

  @Test
  void shouldDecodeToNullWithNullCursor() {
    assertThat(Cursor.decode(null)).isNull();
  }

  @Test
  void shouldDecodeToNullWithEmptyCursor() {
    assertThat(Cursor.decode("")).isNull();
  }

  @Test
  void shouldFailEncodeWithInvalidEntity() {
    assertThatThrownBy(() -> Cursor.encode(new Object[] {new Object()}))
        .isInstanceOf(CamundaSearchException.class)
        .hasMessageContaining("Cannot encode data store pagination information into a cursor")
        .extracting("reason")
        .isEqualTo(Reason.SEARCH_CLIENT_FAILED);
  }

  @Test
  void shouldFailDecodeWithInvalidCursor() {
    assertThatThrownBy(() -> Cursor.decode("invalid_cursor"))
        .isInstanceOf(CamundaSearchException.class)
        .hasMessageContaining("Cannot decode pagination cursor 'invalid_cursor'")
        .extracting("reason")
        .isEqualTo(Reason.INVALID_ARGUMENT);
  }
}
