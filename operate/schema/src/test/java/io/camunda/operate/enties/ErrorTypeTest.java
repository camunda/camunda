/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.enties;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class ErrorTypeTest {

  @ParameterizedTest
  @EnumSource(io.camunda.zeebe.protocol.record.value.ErrorType.class)
  void shouldMapEveryZeebeErrorType(
      final io.camunda.zeebe.protocol.record.value.ErrorType zeebeErrorType) {
    // when
    final ErrorType errorType = ErrorType.fromZeebeErrorType(zeebeErrorType.name());

    // then
    assertThat(errorType)
        .describedAs(
            """
            The enum ErrorType should contain a value for: %s. \
            Probably, the Zeebe error type is new and needs to be added.""",
            zeebeErrorType)
        .isNotNull();

    // every Zeebe error type except UNKNOWN must map to a dedicated value, never the UNKNOWN
    // fallback
    if (zeebeErrorType != io.camunda.zeebe.protocol.record.value.ErrorType.UNKNOWN) {
      assertThat(errorType).isNotEqualTo(ErrorType.UNKNOWN);
    }
  }

  @Test
  void shouldReturnUnspecifiedForNull() {
    // when / then
    assertThat(ErrorType.fromZeebeErrorType(null)).isEqualTo(ErrorType.UNSPECIFIED);
  }

  @Test
  void shouldReturnUnknownForUnmappedValue() {
    // when / then
    assertThat(ErrorType.fromZeebeErrorType("SOME_NEW_ERROR_TYPE")).isEqualTo(ErrorType.UNKNOWN);
  }
}
