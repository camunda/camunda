/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LiteralSubstringStringTypeHandlerTest {

  @ParameterizedTest
  @MethodSource("literalErrorMessages")
  void shouldTransformLiteralErrorMessageToSubstringPattern(
      final String input, final String expected) {
    assertThat(LiteralSubstringStringTypeHandler.transformParameter(input)).isEqualTo(expected);
  }

  private static Stream<Arguments> literalErrorMessages() {
    return Stream.of(
        Arguments.of("Expected result", "%Expected result%"),
        Arguments.of("error 100%", "%error 100\\%%"),
        Arguments.of("line_42", "%line\\_42%"),
        Arguments.of("C:\\errors\\foo", "%C:\\\\errors\\\\foo%"),
        Arguments.of("literal*question?", "%literal*question?%"),
        Arguments.of("C:\\errors_100%", "%C:\\\\errors\\_100\\%%"));
  }
}
