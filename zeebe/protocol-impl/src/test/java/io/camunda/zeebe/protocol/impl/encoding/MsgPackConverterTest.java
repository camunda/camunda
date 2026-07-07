/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;

class MsgPackConverterTest {

  @Test
  void shouldConvertMapToObject() {
    // given
    final Map<String, Object> value = Map.of("key", "value", "count", 42);
    final DirectBuffer buffer = BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(value));

    // when
    final Object result = MsgPackConverter.convertToObject(buffer, Object.class);

    // then
    assertThat(result).isInstanceOf(Map.class).isEqualTo(value);
  }

  @Test
  void shouldConvertArrayToObject() {
    // given
    final List<Object> value = List.of(Map.of("id", 1), Map.of("id", 2));
    final DirectBuffer buffer = BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(value));

    // when
    final Object result = MsgPackConverter.convertToObject(buffer, Object.class);

    // then
    assertThat(result).isInstanceOf(List.class).isEqualTo(value);
  }

  @Test
  void shouldConvertScalarNumberToObject() {
    // given
    final DirectBuffer buffer = BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(42));

    // when
    final Object result = MsgPackConverter.convertToObject(buffer, Object.class);

    // then
    assertThat(result).isInstanceOf(Integer.class).isEqualTo(42);
  }

  @Test
  void shouldConvertScalarBooleanToObject() {
    // given
    final DirectBuffer buffer = BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(true));

    // when
    final Object result = MsgPackConverter.convertToObject(buffer, Object.class);

    // then
    assertThat(result).isInstanceOf(Boolean.class).isEqualTo(true);
  }

  @Test
  void shouldConvertScalarStringToObject() {
    // given — cast to Object forces the value-serialisation overload, not the JSON-text overload
    final DirectBuffer buffer =
        BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack((Object) "hello"));

    // when
    final Object result = MsgPackConverter.convertToObject(buffer, Object.class);

    // then
    assertThat(result).isInstanceOf(String.class).isEqualTo("hello");
  }
}
