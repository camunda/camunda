/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.query;

import static org.assertj.core.api.Assertions.assertThat;

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
  void testEncodeDecode(final String description, final Object[] values) {
    final String encoded = Cursor.encode(values);
    final Object[] decoded = Cursor.decode(encoded);

    assertThat(decoded).usingRecursiveComparison().isEqualTo(values);
  }

  @Test
  void testEncodeWithNull() {
    final String encoded = Cursor.encode(null);

    assertThat(encoded).isNull();
  }

  @Test
  void testEncodeWithEmptyArray() {
    final String encoded = Cursor.encode(new Object[] {});

    assertThat(encoded).isNull();
  }

  @Test
  void testDecodeWithNull() {
    final Object[] decoded = Cursor.decode(null);

    assertThat(decoded).isNull();
  }

  @Test
  void testDecodeWithEmptyString() {
    final Object[] decoded = Cursor.decode("");

    assertThat(decoded).isNull();
  }
}
