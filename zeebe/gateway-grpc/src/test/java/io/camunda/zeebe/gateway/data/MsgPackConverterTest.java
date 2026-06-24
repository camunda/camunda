/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.data;

import static io.camunda.zeebe.util.StringUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.agrona.LangUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessagePacker;

public final class MsgPackConverterTest {

  private static final String JSON = "{\"key1\":1,\"key2\":2}";
  private static final byte[] MSG_PACK = createMsgPack();
  @Rule public final ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldConvertFromJsonStringToMsgPack() {
    // when
    final byte[] msgPack = MsgPackConverter.convertToMsgPack(JSON);

    // then
    assertThat(msgPack).isEqualTo(MSG_PACK);
  }

  @Test
  public void shouldConvertFromJsonStreamToMsgPack() {
    // given
    final byte[] json = getBytes(JSON);
    final InputStream inputStream = new ByteArrayInputStream(json);
    // when
    final byte[] msgPack = MsgPackConverter.convertToMsgPack(inputStream);

    // then
    assertThat(msgPack).isEqualTo(MSG_PACK);
  }

  @Test
  public void shouldConvertFromMsgPackToJsonString() {
    // when
    final String json = MsgPackConverter.convertToJson(MSG_PACK);

    // then
    assertThat(json).isEqualTo(JSON);
  }

  @Test
  public void shouldConvertFromMsgPackToJsonStream() throws Exception {
    // when
    final InputStream jsonStream = MsgPackConverter.convertToJsonInputStream(MSG_PACK);

    // then
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    jsonStream.transferTo(outputStream);
    final byte[] jsonBytes = outputStream.toByteArray();

    assertThat(new String(jsonBytes, StandardCharsets.UTF_8)).isEqualTo(JSON);
  }

  @Test
  public void shouldConvertStringFromMsgPackToJsonString() {
    // when
    final String json =
        MsgPackConverter.convertToJson(
            MsgPackUtil.encodeMsgPack(b -> b.packString("x")).byteArray());

    // then
    assertThat(json).isEqualTo("\"x\"");
  }

  @Test
  public void shouldConvertIntegerFromMsgPackToJsonString() {
    // when
    final String json =
        MsgPackConverter.convertToJson(MsgPackUtil.encodeMsgPack(b -> b.packInt(123)).byteArray());

    // then
    assertThat(json).isEqualTo("123");
  }

  @Test
  public void shouldConvertBooleanFromMsgPackToJsonString() {
    // when
    final String json =
        MsgPackConverter.convertToJson(
            MsgPackUtil.encodeMsgPack(b -> b.packBoolean(true)).byteArray());

    // then
    assertThat(json).isEqualTo("true");
  }

  @Test
  public void shouldConvertArrayFromMsgPackToJsonString() {
    // when
    final String json =
        MsgPackConverter.convertToJson(
            MsgPackUtil.encodeMsgPack(b -> b.packArrayHeader(2).packInt(1).packInt(2)).byteArray());

    // then
    assertThat(json).isEqualTo("[1,2]");
  }

  @Test
  public void shouldConvertNullFromMsgPackToJsonString() {
    // when
    final String json =
        MsgPackConverter.convertToJson(
            MsgPackUtil.encodeMsgPack(MessagePacker::packNil).byteArray());

    // then
    assertThat(json).isEqualTo("null");
  }

  @Test
  public void shouldThrowExceptionIfNotAJsonObject() {
    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("Failed to convert JSON to MessagePack");

    // when
    MsgPackConverter.convertToMsgPack("}");
  }

  @Test
  public void shouldConvertMsgPackToObject() {
    // given
    final var originalObject = new TestObject("test", 123);
    final var msgPack = new UnsafeBuffer(MsgPackConverter.convertToMsgPack(originalObject));

    // when
    final var result = MsgPackConverter.convertToObject(msgPack, TestObject.class);

    // then
    assertThat(result).isEqualTo(originalObject);
  }

  private static byte[] createMsgPack() {
    byte[] msgPack = null;

    try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      final MessagePacker variablesPacker = MessagePack.newDefaultPacker(outputStream);

      variablesPacker.packMapHeader(2).packString("key1").packInt(1).packString("key2").packInt(2);

      variablesPacker.flush();
      msgPack = outputStream.toByteArray();
    } catch (final Exception e) {
      LangUtil.rethrowUnchecked(e);
    }
    return msgPack;
  }

  private record TestObject(String name, int value) {}
}
