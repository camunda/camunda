/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.msgpack;

import static io.zeebe.msgpack.MsgPackUtil.encodeMsgPack;
import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.msgpack.POJO.POJOEnum;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ObjectMappingTest {
  public static final DirectBuffer BUF1 = wrapString("foo");
  public static final DirectBuffer BUF2 = wrapString("bar");
  public static final MutableDirectBuffer MSGPACK_BUF1;
  public static final MutableDirectBuffer MSGPACK_BUF2;
  public static final MutableDirectBuffer MSGPACK_BUF3;

  static {
    MSGPACK_BUF1 =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              w.writeString(BUF1);
              w.writeInteger(123123L);
            });

    MSGPACK_BUF2 =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              w.writeString(BUF2);
              w.writeInteger(24L);
            });

    MSGPACK_BUF3 =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              w.writeString(BUF1);
              w.writeInteger(24L);
            });
  }

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldSerializePOJO() {
    // given
    final POJO pojo = new POJO();
    pojo.setEnum(POJOEnum.BAR);
    pojo.setLong(456456L);
    pojo.setInt(123);
    pojo.setString(BUF1);
    pojo.setBinary(BUF2);
    pojo.setPacked(MSGPACK_BUF1);

    pojo.nestedObject().setLong(24L);

    final int writeLength = pojo.getLength();

    // when
    final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
    pojo.write(resultBuffer, 0);

    // then
    final Map<String, Object> msgPackMap =
        MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
    assertThat(msgPackMap).hasSize(7);
    assertThat(msgPackMap)
        .contains(
            entry("enumProp", POJOEnum.BAR.toString()),
            entry("longProp", 456456L),
            entry("intProp", 123L),
            entry("stringProp", "foo"),
            entry("binaryProp", BUF2.byteArray()));

    @SuppressWarnings("unchecked")
    final Map<String, Object> packedProp = (Map<String, Object>) msgPackMap.get("packedProp");
    assertThat(packedProp).containsExactly(entry("foo", 123123L));

    @SuppressWarnings("unchecked")
    final Map<String, Object> objectProp = (Map<String, Object>) msgPackMap.get("objectProp");
    assertThat(objectProp).containsExactly(entry("foo", 24L));
  }

  @Test
  public void shouldDeserializePOJO() {
    // given
    final POJO pojo = new POJO();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(7);

              w.writeString(wrapString("enumProp"));
              w.writeString(wrapString(POJOEnum.BAR.toString()));

              w.writeString(wrapString("binaryProp"));
              w.writeBinary(BUF1);

              w.writeString(wrapString("stringProp"));
              w.writeString(BUF2);

              w.writeString(wrapString("packedProp"));
              w.writeRaw(MSGPACK_BUF1);

              w.writeString(wrapString("longProp"));
              w.writeInteger(88888L);

              w.writeString(wrapString("intProp"));
              w.writeInteger(123L);

              w.writeString(wrapString("objectProp"));
              w.writeRaw(MSGPACK_BUF1);
            });

    // when
    pojo.wrap(buffer);

    // then
    assertThat(pojo.getEnum()).isEqualByComparingTo(POJOEnum.BAR);
    assertThat(pojo.getLong()).isEqualTo(88888L);
    assertThat(pojo.getInt()).isEqualTo(123);
    assertThatBuffer(pojo.getPacked()).hasBytes(MSGPACK_BUF1);
    assertThatBuffer(pojo.getBinary()).hasBytes(BUF1);
    assertThatBuffer(pojo.getString()).hasBytes(BUF2);
    assertThat(pojo.nestedObject().getLong()).isEqualTo(123123L);
  }

  @Test
  public void shouldNotDeserializePOJOWithWrongValueType() {
    // given
    final POJO pojo = new POJO();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);

              w.writeString(wrapString("stringProp"));
              w.writeFloat(123123.123123d);
            });

    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("Could not deserialize object. Deserialization stuck at offset 13");

    // when
    pojo.wrap(buffer);
  }

  @Test
  public void shouldNotDeserializePOJOWithWrongKeyType() {
    // given
    final POJO pojo = new POJO();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);

              w.writeInteger(123123L);
              w.writeFloat(123123.123123d);
            });

    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("Could not deserialize object. Deserialization stuck at offset 2");

    // when
    pojo.wrap(buffer);
  }

  @Test
  public void shouldNotDeserializePOJOFromNonMap() {
    // given
    final POJO pojo = new POJO();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeString(wrapString("stringProp"));
              w.writeFloat(123123.123123d);
            });

    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("Could not deserialize object. Deserialization stuck at offset 1");

    // when
    pojo.wrap(buffer);
  }

  @Test
  public void shouldFailDeserializationWithMissingRequiredValues() {
    // given
    final POJO pojo = new POJO();

    final DirectBuffer buf1 = encodeMsgPack((w) -> w.writeMapHeader(0));

    // when
    final Throwable error = catchThrowable(() -> pojo.wrap(buf1));

    // then
    assertThat(error)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Could not deserialize object")
        .hasCause(new RuntimeException("Property 'enumProp' has no valid value"));
  }

  @Test
  public void shouldFailDeserializationWithOversizedIntegerValue() {
    // given
    final POJO pojo = new POJO();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);

              w.writeString(wrapString("intProp"));
              w.writeInteger(Integer.MAX_VALUE + 1L);
            });

    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("Could not deserialize object.");

    // when
    pojo.wrap(buffer);
  }

  @Test
  public void shouldFailDeserializationWithUndersizedIntegerValue() {
    // given
    final POJO pojo = new POJO();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(6);

              w.writeString(wrapString("enumProp"));
              w.writeString(wrapString(POJOEnum.BAR.toString()));

              w.writeString(wrapString("binaryProp"));
              w.writeBinary(BUF1);

              w.writeString(wrapString("stringProp"));
              w.writeString(BUF2);

              w.writeString(wrapString("packedProp"));
              w.writeRaw(MSGPACK_BUF1);

              w.writeString(wrapString("longProp"));
              w.writeInteger(88888L);

              w.writeString(wrapString("intProp"));
              w.writeInteger(Integer.MIN_VALUE - 1L);
            });

    // then
    exception.expect(RuntimeException.class);
    exception.expectMessage("Could not deserialize object.");

    // when
    pojo.wrap(buffer);
  }

  @Test
  public void shouldFailSerializationWithMissingRequiredValues() {
    // given
    final POJO pojo = new POJO();

    final UnsafeBuffer buf = new UnsafeBuffer(new byte[1024]);

    // then
    exception.expect(MsgpackPropertyException.class);
    exception.expectMessage(
        "Property 'enumProp' is invalid: Expected a value or default value to be set before writing, but has nothing");

    // when
    pojo.write(buf, 0);
  }

  @Test
  public void shouldFailLengthEstimationWithMissingRequiredValues() {
    // given
    final POJO pojo = new POJO();

    // then
    exception.expect(MsgpackPropertyException.class);
    exception.expectMessage(
        "Property 'enumProp' is invalid: Expected a value or default value to be specified, but has nothing");

    // when
    pojo.getLength();
  }

  @Test
  public void shouldDeserializeWithReusedPOJO() {
    // given
    final POJO pojo = new POJO();

    final DirectBuffer buf1 =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(7);

              w.writeString(wrapString("enumProp"));
              w.writeString(wrapString(POJOEnum.BAR.toString()));

              w.writeString(wrapString("binaryProp"));
              w.writeBinary(BUF1);

              w.writeString(wrapString("stringProp"));
              w.writeString(BUF2);

              w.writeString(wrapString("packedProp"));
              w.writeRaw(MSGPACK_BUF1);

              w.writeString(wrapString("longProp"));
              w.writeInteger(88888L);

              w.writeString(wrapString("intProp"));
              w.writeInteger(123);

              w.writeString(wrapString("objectProp"));
              w.writeRaw(MSGPACK_BUF1);
            });
    pojo.wrap(buf1);

    final DirectBuffer buf2 =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(7);

              w.writeString(wrapString("enumProp"));
              w.writeString(wrapString(POJOEnum.FOO.toString()));

              w.writeString(wrapString("binaryProp"));
              w.writeBinary(BUF2);

              w.writeString(wrapString("stringProp"));
              w.writeString(BUF1);

              w.writeString(wrapString("packedProp"));
              w.writeRaw(MSGPACK_BUF2);

              w.writeString(wrapString("longProp"));
              w.writeInteger(7777L);

              w.writeString(wrapString("intProp"));
              w.writeInteger(456);

              w.writeString(wrapString("objectProp"));
              w.writeRaw(MSGPACK_BUF3);
            });

    // when
    pojo.reset();
    pojo.wrap(buf2);

    // then
    assertThat(pojo.getEnum()).isEqualByComparingTo(POJOEnum.FOO);
    assertThat(pojo.getLong()).isEqualTo(7777L);
    assertThat(pojo.getInt()).isEqualTo(456);
    assertThatBuffer(pojo.getPacked()).hasBytes(MSGPACK_BUF2);
    assertThatBuffer(pojo.getBinary()).hasBytes(BUF2);
    assertThatBuffer(pojo.getString()).hasBytes(BUF1);
    assertThat(pojo.nestedObject().getLong()).isEqualTo(24L);
  }
}
