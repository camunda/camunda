/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.stream.impl.state.NextValue;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class NextValueTest {
  @Test
  public void shouldNotSerializeWithoutValue() {
    final var value = new NextValue();

    final var writer = new MsgPackWriter();
    final var buffer = new UnsafeBuffer(new byte[1024]);
    writer.wrap(buffer, 0);

    assertThatThrownBy(() -> value.write(writer)).isInstanceOf(MsgpackPropertyException.class);
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, -100, (long) Integer.MIN_VALUE, Long.MIN_VALUE})
  public void shouldRequirePositiveValueInSetter(final long v) {
    final var value = new NextValue();
    assertThatThrownBy(() -> value.set(v))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid value: expected positive number, but got " + v);
  }
}
