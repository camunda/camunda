/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack;

import static io.zeebe.msgpack.MsgPackUtil.encodeMsgPack;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.msgpack.value.ValueArray;
import java.util.Iterator;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class POJOArrayTest {
  @Rule public final ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldSerializePOJO() {
    // given
    final POJOArray pojo = new POJOArray();
    final ValueArray<MinimalPOJO> iterator1 = pojo.simpleArray();
    iterator1.add().setLongProp(123L);
    iterator1.add().setLongProp(456L);
    iterator1.add().setLongProp(789L);

    final int writeLength = pojo.getLength();

    // when
    final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
    pojo.write(resultBuffer, 0);

    // then
    final Map<String, Object> msgPackMap =
        MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
    assertThat(msgPackMap)
        .containsOnly(entry("simpleArray", "[{longProp=123}, {longProp=456}, {longProp=789}]"));
  }

  @Test
  public void shouldSerializePOJOWithEmptyArray() {
    // given
    final POJOArray pojo = new POJOArray();

    final int writeLength = pojo.getLength();

    // when
    final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
    pojo.write(resultBuffer, 0);

    // then
    final Map<String, Object> msgPackMap =
        MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
    assertThat(msgPackMap).containsOnly(entry("simpleArray", "[]"));
  }

  @Test
  public void shouldSerializePOJOAfterReset() {
    // given
    final POJOArray pojo = new POJOArray();
    pojo.simpleArray().add().setLongProp(124);
    pojo.reset();

    final int writeLength = pojo.getLength();

    // when
    final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
    pojo.write(resultBuffer, 0);

    // then
    final Map<String, Object> msgPackMap =
        MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
    assertThat(msgPackMap).containsOnly(entry("simpleArray", "[]"));
  }

  @Test
  public void shouldSerializePOJOWithDefaultValues() {
    // given
    final POJOArray pojo = new POJOArray();
    final ValueArray<MinimalPOJO> iterator1 = pojo.simpleArray();
    iterator1.add().setLongProp(123L);

    final int writeLength = pojo.getLength();

    // when
    final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
    pojo.write(resultBuffer, 0);

    // then
    final Map<String, Object> msgPackMap =
        MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
    assertThat(msgPackMap).containsOnly(entry("simpleArray", "[{longProp=123}]"));
  }

  @Test
  public void shouldSerializeAfterPartiallyReadEntries() {
    // given
    final POJOArray pojo = new POJOArray();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              encodeSimpleArrayProp(w);
            });

    pojo.wrap(buffer);
    final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
    iterator.next();
    iterator.next();
    iterator.next();

    final int writeLength = pojo.getLength();

    // when
    final UnsafeBuffer pojoBuffer = new UnsafeBuffer(new byte[writeLength]);
    pojo.write(pojoBuffer, 0);

    // then
    final Map<String, Object> msgPackMap = MsgPackUtil.asMap(pojoBuffer, 0, pojoBuffer.capacity());
    assertThat(msgPackMap)
        .containsOnly(
            entry(
                "simpleArray",
                "[{longProp=123}, {longProp=456}, {longProp=789}, {longProp=555}, {longProp=777}]"));
  }

  @Test
  public void shouldNotSerializeRemovedEntry() {
    // given
    final POJOArray pojo = new POJOArray();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              encodeSimpleArrayProp(w);
            });

    pojo.wrap(buffer);
    final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
    iterator.next();
    iterator.next();
    iterator.next();

    // when
    iterator.remove();

    // then
    final int writeLength = pojo.getLength();
    final UnsafeBuffer pojoBuffer = new UnsafeBuffer(new byte[writeLength]);
    pojo.write(pojoBuffer, 0);

    final Map<String, Object> msgPackMap = MsgPackUtil.asMap(pojoBuffer, 0, pojoBuffer.capacity());
    assertThat(msgPackMap)
        .containsOnly(
            entry(
                "simpleArray", "[{longProp=123}, {longProp=456}, {longProp=555}, {longProp=777}]"));
  }

  @Test
  public void shouldSerializeAppendedEntry() {
    // given
    final POJOArray pojo = new POJOArray();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              encodeSimpleArrayProp(w);
            });

    pojo.wrap(buffer);
    final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
    iterator.next();
    iterator.next();
    iterator.next();
    iterator.next();
    iterator.next();

    // when
    pojo.simpleArray().add().setLongProp(999L);

    // then
    final int writeLength = pojo.getLength();
    final UnsafeBuffer pojoBuffer = new UnsafeBuffer(new byte[writeLength]);
    pojo.write(pojoBuffer, 0);

    final Map<String, Object> msgPackMap = MsgPackUtil.asMap(pojoBuffer, 0, pojoBuffer.capacity());
    assertThat(msgPackMap)
        .containsOnly(
            entry(
                "simpleArray",
                "[{longProp=123}, {longProp=456}, {longProp=789}, {longProp=555}, {longProp=777}, {longProp=999}]"));
  }

  @Test
  public void shouldSerializeInbetweenAddedEntry() {
    // given
    final POJOArray pojo = new POJOArray();
    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              encodeSimpleArrayProp(w);
            });

    pojo.wrap(buffer);
    final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
    iterator.next();
    iterator.next();
    iterator.next();

    // when
    pojo.simpleArrayProp.add().setLongProp(999L);

    // then
    final int writeLength = pojo.getLength();
    final UnsafeBuffer pojoBuffer = new UnsafeBuffer(new byte[writeLength]);
    pojo.write(pojoBuffer, 0);

    final Map<String, Object> msgPackMap = MsgPackUtil.asMap(pojoBuffer, 0, pojoBuffer.capacity());
    assertThat(msgPackMap)
        .containsOnly(
            entry(
                "simpleArray",
                "[{longProp=123}, {longProp=456}, {longProp=789}, {longProp=999}, {longProp=555}, {longProp=777}]"));
  }

  @Test
  public void shouldDeserializePOJO() {
    // given
    final POJOArray pojo = new POJOArray();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(3);
              encodeSimpleArrayProp(w);

              w.writeString(wrapString("emptyDefaultArray"));
              w.writeArrayHeader(1);

              w.writeMapHeader(1);
              w.writeString(wrapString("longProp"));
              w.writeInteger(753L);

              w.writeString(wrapString("notEmptyDefaultArray"));
              w.writeArrayHeader(0);
            });

    // when
    pojo.wrap(buffer);

    // then
    final Iterator<MinimalPOJO> iterator1 = pojo.simpleArray().iterator();
    assertThat(iterator1.hasNext()).isTrue();
    assertThat(iterator1.next().getLongProp()).isEqualTo(123L);
    assertThat(iterator1.hasNext()).isTrue();
    assertThat(iterator1.next().getLongProp()).isEqualTo(456L);
    assertThat(iterator1.hasNext()).isTrue();
    assertThat(iterator1.next().getLongProp()).isEqualTo(789L);
    assertThat(iterator1.hasNext()).isTrue();
    assertThat(iterator1.next().getLongProp()).isEqualTo(555L);
    assertThat(iterator1.hasNext()).isTrue();
    assertThat(iterator1.next().getLongProp()).isEqualTo(777L);
    assertThat(iterator1.hasNext()).isFalse();
  }

  @Test
  public void shouldDeserializePOJOWithDefaultValues() {
    // given
    final POJOArray pojo = new POJOArray();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              encodeSimpleArrayProp(w);
            });

    // when
    pojo.wrap(buffer);

    // then
    final Iterator<MinimalPOJO> iterator1 = pojo.simpleArray().iterator();
    assertThat(iterator1.hasNext()).isTrue();
    assertThat(iterator1.next().getLongProp()).isEqualTo(123L);
    assertThat(iterator1.hasNext()).isTrue();
    assertThat(iterator1.next().getLongProp()).isEqualTo(456L);
    assertThat(iterator1.hasNext()).isTrue();
    assertThat(iterator1.next().getLongProp()).isEqualTo(789L);
    assertThat(iterator1.hasNext()).isTrue();
    assertThat(iterator1.next().getLongProp()).isEqualTo(555L);
    assertThat(iterator1.hasNext()).isTrue();
    assertThat(iterator1.next().getLongProp()).isEqualTo(777L);
    assertThat(iterator1.hasNext()).isFalse();
  }

  @Test
  public void shouldFailOnInitialRemove() {
    // given
    final POJOArray pojo = new POJOArray();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              encodeSimpleArrayProp(w);
            });

    pojo.wrap(buffer);
    final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();

    // then
    exception.expect(IllegalStateException.class);

    // when
    iterator.remove();
  }

  @Test
  public void shouldFailOnRemovingEntryTwice() {
    // given
    final POJOArray pojo = new POJOArray();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              encodeSimpleArrayProp(w);
            });

    pojo.wrap(buffer);
    final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
    iterator.next();
    iterator.remove();

    // then
    exception.expect(IllegalStateException.class);

    // when
    iterator.remove();
  }

  @Test
  public void shouldFailOnRemovingWhenEntryHasBeenAddedBefore() {
    // given
    final POJOArray pojo = new POJOArray();

    final DirectBuffer buffer =
        encodeMsgPack(
            (w) -> {
              w.writeMapHeader(1);
              encodeSimpleArrayProp(w);
            });

    pojo.wrap(buffer);
    final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
    iterator.next();
    pojo.simpleArray().add().setLongProp(999L);

    // then
    exception.expect(IllegalStateException.class);

    // when
    iterator.remove();
  }

  @Test
  public void shouldAddFirstEntryToSimpleArrayProp() {
    // given
    final POJOArray pojo = new POJOArray();
    final ValueArray<MinimalPOJO> iterator = pojo.simpleArray();

    // when
    iterator.add().setLongProp(741L);

    // then
    final int length = pojo.getLength();
    final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[length]);
    pojo.write(resultBuffer, 0);

    final Map<String, Object> msgPackMap =
        MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
    assertThat(msgPackMap).containsOnly(entry("simpleArray", "[{longProp=741}]"));
  }

  @Test
  public void shouldIterateOverModifiedArray() {
    // given
    final POJOArray pojo = new POJOArray();
    final ValueArray<MinimalPOJO> array = pojo.simpleArray();

    // when
    array.add().setLongProp(123L);

    // then
    final Iterator<MinimalPOJO> iterator = array.iterator();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next().getLongProp()).isEqualTo(123L);
    assertThat(iterator.hasNext()).isFalse();
  }

  protected void encodeSimpleArrayProp(final MsgPackWriter writer) {
    writer.writeString(wrapString("simpleArray"));
    writer.writeArrayHeader(5);

    writer.writeMapHeader(1);
    writer.writeString(wrapString("longProp"));
    writer.writeInteger(123L);

    writer.writeMapHeader(1);
    writer.writeString(wrapString("longProp"));
    writer.writeInteger(456L);

    writer.writeMapHeader(1);
    writer.writeString(wrapString("longProp"));
    writer.writeInteger(789L);

    writer.writeMapHeader(1);
    writer.writeString(wrapString("longProp"));
    writer.writeInteger(555L);

    writer.writeMapHeader(1);
    writer.writeString(wrapString("longProp"));
    writer.writeInteger(777L);
  }
}
