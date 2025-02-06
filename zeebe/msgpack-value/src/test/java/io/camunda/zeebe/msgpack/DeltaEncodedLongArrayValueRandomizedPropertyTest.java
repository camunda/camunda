/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.DeltaEncodedLongArrayValue;
import java.util.Arrays;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.agrona.concurrent.UnsafeBuffer;

final class DeltaEncodedLongArrayValueRandomizedPropertyTest {
  private final MsgPackWriter writer = new MsgPackWriter();
  private final MsgPackReader reader = new MsgPackReader();

  @Property
  void shouldEncodeAndDecodeAnyArray(@ForAll final long[] array) {
    // given
    final var input = new DeltaEncodedLongArrayValue();
    final var result = new DeltaEncodedLongArrayValue();
    input.setValues(array);

    // when
    final int encodedLength = input.getEncodedLength();
    final var buffer = new UnsafeBuffer(new byte[encodedLength]);

    writer.wrap(buffer, 0);
    input.write(writer);

    reader.wrap(buffer, 0, encodedLength);
    result.read(reader);

    // then
    assertArrayEquals(array, input.getValues());
    assertArrayEquals(array, result.getValues());
  }

  @Property
  void shouldWritePlainJson(@ForAll final long[] array) {
    // given
    final var input = new DeltaEncodedLongArrayValue();
    input.setValues(array);

    // when
    final var output = new StringBuilder();
    input.writeJSON(output);

    // then
    assertThat(output.toString())
        .isEqualTo(
            "[" + String.join(",", Arrays.stream(array).mapToObj(Long::toString).toList()) + "]");
  }
}
