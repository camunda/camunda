/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.value.DeltaEncodedLongArrayValue;
import org.junit.jupiter.api.Test;

final class DeltaEncodedLongArrayValueTest {
  @Test
  void shouldEncodeEfficiently() {
    // given
    final var value = new DeltaEncodedLongArrayValue();
    value.setValues(
        new long[] {
          // Emulates how we generate keys in Protocol.java without adding a cyclic dependency
          (3L << 51) + 1L,
          (3L << 51) + 1000L,
          (3L << 51) + 1234L,
          (3L << 51) + 1238L,
          (3L << 51) + 2127L
        });

    // when
    final int compressedLength = value.getEncodedLength();

    // then
    assertThat(compressedLength).isEqualTo(19);
  }

  @Test
  void shouldWriteExpectedJson() {
    // given
    final var value = new DeltaEncodedLongArrayValue();
    value.setValues(new long[] {1, 1000, 1234, 1238, 2127});

    // when
    final var output = new StringBuilder();
    value.writeJSON(output);

    // then
    assertThat(output.toString()).isEqualTo("[1,1000,1234,1238,2127]");
  }
}
